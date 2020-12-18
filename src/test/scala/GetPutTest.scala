import akka.actor.{Actor, ActorSystem, Props}
import components.{Host, Metadata, Server, Version}
import components.Message.{Failure, Get, GetResult, Put, PutResult, UpdateConfiguration}
import junit.framework.Assert.{assertEquals, assertTrue}
import junit.framework.TestCase

class GetPutTest extends TestCase {

    override def setUp(): Unit = {

    }

    implicit def implicitIntToString(x: Int): String = {
        x.toString
    }

    def testGoodPutGet(): Unit = {
        val metaData = new Metadata(2, 2, 1)

        val system = ActorSystem("KV")
        val server = system.actorOf(Props(new Server("server", metaData)), name = "server")
        metaData.addHost(Host("server"), server)

        val client = system.actorOf(Props(new Actor {
            override def preStart: Unit = {
                server ! Put("hello", "apple", Version())
                server ! Get("aa")
            }

            override def receive: Receive = {
                case PutResult(_, _) =>
                    println("put success")
                case GetResult(_, x, _) =>
                    println(x)
                case Failure() =>
                    println("fail")
            }
        }))

        Thread.sleep(5000)
        system.terminate()
    }

    def testBadPutGet(): Unit = {
        val metaData = new Metadata(2, 2, 1)

        val system = ActorSystem("KV")
        val server = system.actorOf(Props(new Server("server", metaData)), name = "server")
        metaData.addHost(Host("server"), server)

        val client = system.actorOf(Props(new Actor {
            override def preStart: Unit = {
                server ! Put("hello", "apple", Version())
                server ! Get("aa")
            }

            override def receive: Receive = {
                case PutResult(_, _) =>
                    println("put success")
                case GetResult(_, x, _) =>
                    println(x)
                case Failure() =>
                    println("fail")
            }
        }))

        Thread.sleep(5000)
        system.terminate()
    }

}
