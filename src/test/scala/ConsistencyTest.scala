import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import components.Message.{ExtraInfo, Get, GetResult, PeekStorage, Put, UpdateConfiguration, UpdateFuzzParams}
import components.{Host, Metadata, Server, Version}
import environment.{FuzzParams, Fuzzed}
import junit.framework.TestCase
import myutils.ExpRandomGenerator

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

class ConsistencyTest extends TestCase {

    def getConcurrentConsistency(arsMeanDelay: Double, writeMeanDelay: Double, timeAfterWrite: List[Double], quorumW: Int, quorumR: Int, replicaN: Int, serverNum: Int): List[Double] = {
        val rand = ExpRandomGenerator
        val totalTrials = 5000
        val iterations = 1 to totalTrials
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
            writeDelay = rand.nextExp(1 / writeMeanDelay),
            arsDelay = rand.nextExp(1 / arsMeanDelay),
            dropRate = 0
        )))

        // wait for sync
        Thread.sleep(3000)

        val counter = system.actorOf(Props(new Actor with Timers {

            object End

            var trialResult: List[Int] = timeAfterWrite.map(_ => 0)
            var completeTrial: Int = 0
            var caller: Option[ActorRef] = None

            override def receive: Receive = {
                case subResult: List[Int] =>
                    trialResult = (trialResult zip subResult).map { case (x, y) => x + y }

                    completeTrial += 1
                    if (completeTrial >= totalTrials) {
                        self ! End
                    }
                case "get" =>
                    caller = Some(sender())
                case End =>
                    if (caller.isDefined) {
                        caller.get ! trialResult
                    }
                case _ =>
            }
        }), "counter")

        (iterations zip queries) foreach { case (i, (key, value)) =>
            if (i % 100 == 0) {
                Thread.sleep(500)
            }
            // we do iteration times experiments
            val target = Server.redirectToCoordinator(metaData, key).get
            target ! Put(key, value, Version())
            val client = system.actorOf(Props(new Actor with Fuzzed {
                var count = 0
                var subResult: List[Int] = List()

                override def receive: Receive = {
                    case GetResult(_, _, allReplicas) =>
                        if (allReplicas.contains(None)) {
                            subResult = 0 :: subResult
                        } else {
                            subResult = 1 :: subResult
                        }
                        count += 1
                        if (count >= timeAfterWrite.length) {
                            counter ! subResult.reverse.sorted
                            context.system.stop(self)
                        }
                    case _ =>
                }

                override def preStart(): Unit = {
                    timeAfterWrite.foreach(t => send(target, Get(key, ExtraInfo(true)), t, 0))
                }
            }), s"client_$i")
        }

        implicit val timeout: Timeout = Timeout(30.seconds)
        val trialResult = Await.result(counter ? "get", timeout.duration).asInstanceOf[List[Int]]
//        println(trialResult, totalTrials)

        trialResult.map(x => x.toDouble / totalTrials)
    }

    def testConcurrentConsistency(): Unit = {
        val timeSerials = List(2, 5, 10, 20, 50, 100, 200, 300).map(_.toDouble)
        val WS = List(100, 100, 100, 100, 100, 100)
        val ARS = List(5, 10, 20, 50, 100, 200)
        val R = 2
        val W = 1
        val N = 3
        (WS zip ARS).foreach { case (ws, ars) =>
            println(s"==============$ws, $ars==============")
            val ret = getConcurrentConsistency(ars, ws, timeSerials, quorumR = R + 1, quorumW = W + 1, replicaN = N + 1, serverNum = 50)
            ret.foreach(println)
        }
    }

    def testQuorumConsistency(): Unit = {
        val timeSerials = List(5, 10, 15, 20, 30, 50, 100, 150, 200, 250).map(_.toDouble)
        val WS = 50
        val ARS = 10
        val N = List(3,5,7,9,11)
        val quorum_R = 2
        val quorum_W = 1
        val repeat = 2
        N.map { replicaN =>
            //            val ret = (1 to repeat).map(_ => {
            //                println(s"============$replicaN============")
            //                getConcurrentConsistency(WS, ARS, timeSerials, quorumR = quorum_R + 1, quorumW = quorum_W + 1, replicaN = replicaN + 1, serverNum = 100)
            //            }).reduce((l1, l2) => (l1 zip l2).map{case (x,y) => x+y}).map(_ / repeat)
            println(replicaN)
            val ret = getConcurrentConsistency(WS, ARS, timeSerials, quorumR = quorum_R + 1, quorumW = quorum_W + 1, replicaN = replicaN + 1, serverNum = 100)
            ret.map(println)
        }

    }

    def getIdealConsistency(arsMeanDelay: Double, writeMeanDelay: Double, timeAfterWrite: Double, quorumW: Int, quorumR: Int, replicaN: Int): Double = {
        var consistentTrials = 0
        val rand = ExpRandomGenerator
        val iterations = 5000
        val replicas = 0 until replicaN
        for (elem <- (1 to iterations)) {
            val Ws = replicas.map(replica => rand.nextExp(1 / writeMeanDelay))
            val As = replicas.map(replica => rand.nextExp(1 / arsMeanDelay))
            val Rs = replicas.map(replica => rand.nextExp(1 / arsMeanDelay))
            val Ss = replicas.map(replica => rand.nextExp(1 / arsMeanDelay))
            val writesLatency = (Ws zip As).map { case (x, y) => x + y }
            val readLatency = (Rs zip Ss).map { case (x, y) => x + y }
            val writeFinish = writesLatency.sorted.toList(quorumW - 1)
            val readFinish = readLatency.sorted.toList(quorumR - 1)
            val replyReplicas = replicas.filter(i => readLatency(i) <= readFinish)
            val consistentNum = replyReplicas.map(i => {
                if (writeFinish + Rs(i) + timeAfterWrite >= Ws(i)) 1 else 0
            }).sum
            //            println(consistentNum)
            consistentTrials += (if (consistentNum > 0) 1 else 0)
        }
        //        println(consistentTrials)
        consistentTrials.toDouble / iterations
    }

    def testIdealConsistency(): Unit = {
        val timeSerials = List(1, 2, 4, 6, 8, 10, 20, 50, 100)
        val WS = List(20, 10, 5, 5, 10, 20)
        val ARS = List(10, 5, 2, 10, 20)
    }
}
