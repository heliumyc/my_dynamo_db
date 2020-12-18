import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import components.Message.{Get, GetResult, PeekStorage, Put, UpdateConfiguration, UpdateFuzzParams}
import components.{Host, Metadata, Server, Version}
import environment.{FuzzParams, Fuzzed}
import junit.framework.TestCase
import myutils.ExpRandomGenerator

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

class ConsistencyTest extends TestCase {

    def getConsistency(arsMeanDelay: Double, writeMeanDelay: Double, timeAfterWrite: Double, quorumW: Int, quorumR: Int, replicaN: Int, serverNum: Int): Double = {
        val rand = ExpRandomGenerator
        val iterations = 1 to 1
        // generate pseudo query for every iteration
        val queries = iterations.map(i => {
            val key = s"user $i"
            val value = s"value for user $i"
            (key, value)
        })
        // start the system
        val metaData = new Metadata(quorumW, quorumR, replicaN)
        metaData.enableReadRepair = false
        val serverList = (1 to serverNum).map(i => s"server_$i")

        val system = ActorSystem("KV")
        val serverRefs = serverList.map(x => system.actorOf(Props(new Server(x, metaData)), name = x))
        serverRefs.foreach(ref => metaData.addHost(Host(ref.path.name), ref))
        serverRefs.foreach(ref => ref ! UpdateConfiguration(metaData))
        serverRefs.foreach(ref => ref ! UpdateFuzzParams(FuzzParams(
            writeDelay = rand.nextExp(1/writeMeanDelay),
            arsDelay = rand.nextExp(1/arsMeanDelay),
            dropRate = 0
        )))

        // wait for sync
        Thread.sleep(1000)

        val counter = system.actorOf(Props(new Actor with Timers {

            object End

            var consistentTrial = 0
            var caller: Option[ActorRef] = None
            timers.startSingleTimer(End, End, 5.seconds)

            override def receive: Receive = {
                case x: Int => consistentTrial += 1
                case "get" => caller = Some(sender())
                case End =>
                    if (caller.isDefined) {
                        caller.get ! consistentTrial
                    }
                case _ =>
            }
        }), "counter")

        (iterations zip queries) foreach { case (i, (key, value)) =>
            // we do iteration times experiments
            val target = Server.redirectToCoordinator(metaData, key).get
            val client = system.actorOf(Props(new Actor with Fuzzed {
                override def receive: Receive = {
                    case GetResult(key, Some(record), allReplicas) =>
                        if (value == record.value) {
                            send(counter, 1, 0, 0)
                        }
                        context.system.stop(self)
                    case GetResult(key, None, _) =>
                        context.system.stop(self)
                    case _ =>
                }

                override def preStart(): Unit = {
                    // put key value into storage
                    send(target, Put(key, value, Version()), 0, 0)
                    // after time t, read from system
                    send(target, Get(key), timeAfterWrite, 0)
                }
            }), s"client_$i")
        }

        implicit val timeout: Timeout = Timeout(5.seconds)
        val trialResult = Await.result(counter ? "get", 10.seconds).asInstanceOf[Int]
        println(trialResult)

        trialResult.toDouble / iterations.length
    }

    def testConsistency(): Unit = {
        getConsistency(5, 20, 1, quorumR = 1, quorumW = 1, replicaN = 3, serverNum = 10)
    }
}
