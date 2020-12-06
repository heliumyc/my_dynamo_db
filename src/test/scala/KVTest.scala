import akka.actor.{Actor, ActorSystem, Props}
import common.Message.{Get, Put, Reply}
import components.PhysicalNode
import junit.framework.Assert.assertEquals
import junit.framework.TestCase

class KVTest extends TestCase {


    override def setUp(): Unit = {

    }

    def testPutGet(): Unit = {
        val system = ActorSystem("Counter")
        val server = system.actorOf(Props(new PhysicalNode(1)), name = "server")

        val client = system.actorOf(Props(new Actor {
            override def receive: Receive = {
                case Reply(x) =>
                    assertEquals(x, "fuck")
                    context.system.terminate()
            }

            override def preStart: Unit = {
                server ! Put("hello", "fuck")
                server ! Get("hello")
            }
        }))

    }

}
