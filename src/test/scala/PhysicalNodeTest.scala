import akka.actor.{Actor, ActorSystem, Props}
import components.{Configuration, PhysicalNode, VectorClock}
import components.PhysicalNode.{Get, Put, Result}
import junit.framework.Assert.assertEquals
import junit.framework.TestCase

class PhysicalNodeTest extends TestCase {

    override def setUp(): Unit = {

    }

    def testPutGet(): Unit = {
        val system = ActorSystem("KV")
        val serverName = "server"
        val config = Configuration(Set(serverName), Set(), serverName, VectorClock(Map()))
        val server = system.actorOf(Props(new PhysicalNode(1.toString, config)), name = serverName)

        val client = system.actorOf(Props(new Actor {
            override def receive: Receive = {
                case Result(_, x) =>
                    assertEquals(x, Some("fuck"))
                    context.system.terminate()
            }

            override def preStart: Unit = {
                server ! Put("hello", "fuck")
                server ! Get("hello")
            }
        }))

    }

}
