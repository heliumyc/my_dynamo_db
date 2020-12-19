import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.{after, ask}
import akka.util.Timeout
import components.GossipActor._
import components.{GossipActor, Host, Server}
import environment.{Fuzzed, MessageLogging}
import junit.framework.TestCase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

class GossipTest extends TestCase {

    def testGossipAdd(): Unit = {
        val serverNum = 5
        val serverList = (1 to serverNum).map(i => s"server_$i").map(Host)
        val system = ActorSystem("KV")
        val serverRefs = serverList.map(x => system.actorOf(Props(new GossipActor(x)), name = x.address))
        var router = (serverList zip serverRefs).toMap
        serverRefs.foreach(ref => ref ! SetRouter(router))
        serverRefs.foreach(ref => ref ! SetMembership(serverList.toList))

        // prepare a new server
        val newServer = Host("new_comer")
        val newServerRef = system.actorOf(Props(new GossipActor(newServer)), name = newServer.address)
        router += newServer -> newServerRef
        serverRefs.foreach(ref => ref ! SetRouter(router))

        // new comer
        serverRefs.head ! AddNewMember(newServer)

        Thread.sleep(1000)
        // show all members
        system.actorOf(Props(new Actor with Timers with MessageLogging {
            override def receive: Receive = {
                case Membership(who, member) =>
                    println(s"$who has $member")
            }

            override def preStart(): Unit = {
                serverRefs.foreach(ref => ref ! GetMemberShip())
            }
        }), name=s"client")
        Thread.sleep(1000)
    }

    def testConverge(): Unit = {
        val serverNum = 500
        var serverList = (1 to serverNum).map(i => s"server_$i").map(Host)
        val system = ActorSystem("KV")
        val serverRefs = serverList.map(x => system.actorOf(Props(new GossipActor(x)), name = x.address))
        var router = (serverList zip serverRefs).toMap
        serverRefs.foreach(ref => ref ! SetRouter(router))
        serverRefs.foreach(ref => ref ! SetMembership(serverList.toList))

        // add a new server
        val newServer = Host("new_comer")
        val newServerRef = system.actorOf(Props(new GossipActor(newServer)), name = newServer.address)
        router += newServer -> newServerRef
        serverRefs.foreach(ref => ref ! SetRouter(router))

        Thread.sleep(1000)

        val interval = 50
        val timeSerials = (0 to 5000 by interval).toList
        val reducer = system.actorOf(Props(new Actor with Timers {
            var count: Int = timeSerials.length
            var syncs: List[Int] = List()
            var caller: Option[ActorRef] = None

            override def receive: Receive = {
                case x: Int =>
                    count -= 1
                    syncs = x::syncs
                    if (count == 0) {
                        if (caller.isDefined) {
                            caller.get ! syncs.sorted.map(_.toDouble/(serverNum+1))
                        }
                    }
                case "get" => caller = Some(sender())
            }
        }))

        val clients = timeSerials.map(time => {
            system.actorOf(Props(new Actor with Timers with MessageLogging {
                var syncSuccess:Int = 0
                var count: Int = serverNum + 1

                override def receive: Receive = {
                    case Membership(who, member) =>
                        count -= 1
                        if (member.length == serverNum + 1) {
                            syncSuccess += 1
                        }
                        if (count == 0) {
                            reducer ! syncSuccess
                        }
                }

                override def preStart(): Unit = {
                    serverRefs.head ! AddNewMember(newServer)
                    after(time.milliseconds)(Future {
                        serverRefs.foreach(ref => ref ! GetMemberShip())
                        newServerRef ! GetMemberShip()
                    })(context.system)
                }
            }), name=s"client_$time")
        })

        implicit val timeout: Timeout = Timeout(20.seconds)
        val trialResult = Await.result(reducer ? "get", timeout.duration).asInstanceOf[List[Int]]
        println(trialResult)
        trialResult.foreach(println)
    }

}
