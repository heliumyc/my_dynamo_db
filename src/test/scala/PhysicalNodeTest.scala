import akka.actor.{Actor, ActorSystem, Props}
import components.{Metadata, PhysicalNode}
import components.PhysicalNode.{Get, Put, Result, UpdateConfiguration}
import junit.framework.Assert.{assertEquals, assertTrue}
import junit.framework.TestCase

class PhysicalNodeTest extends TestCase {

    override def setUp(): Unit = {

    }

    def testPutGet(): Unit = {
        val metaData = new Metadata(2, 2, 3)

        val system = ActorSystem("KV")
        val server = system.actorOf(Props(new PhysicalNode(1.toString, metaData)), name = "server")
        metaData.addPhysicalNode("server", server)

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

    def testReplication(): Unit = {
        val metaData = new Metadata(2, 2, 3)
        val serverList = List("node1", "node2", "node3", "node4", "node5")

        val system = ActorSystem("KV")
        val serverRefs = serverList.map(x => system.actorOf(Props(new PhysicalNode(x, metaData)), name = x))
        serverRefs.foreach(ref => metaData.addPhysicalNode(ref.path.name, ref))
        serverRefs.foreach(ref => ref ! UpdateConfiguration(metaData))

        val target = serverRefs(serverList.indexOf(metaData.partition.getServer("hello").get))
        target ! Put("hello", "123")
        // wait for replication consistent
        Thread.sleep(2000)

        val client = system.actorOf(Props(new Actor {
            var count = 0
            override def receive: Receive = {
                case Result(key, Some(value)) =>
                    assertEquals(value, "123")
                    count += 1
                case Result(key, None) =>
                case "end" =>
                    println(count)
            }

            override def preStart(): Unit = {
                serverRefs.foreach(ref => ref ! Get("hello"))
            }
        }))

        Thread.sleep(2000)
        client ! "end"
        system.terminate()
    }

}
