import akka.actor.{Actor, ActorSystem, Props}
import components.Message._
import components.{Host, Metadata, Server, Version}
import junit.framework.TestCase

class ReplicationTest extends TestCase {

    override def setUp(): Unit = {

    }

    implicit def implicitIntToString(x: Int): String = {
        x.toString
    }

    def testReplication(): Unit = {
        val metaData = new Metadata(2, 2, 3)
        val serverList = List("node1", "node2", "node3", "node4", "node5")

        val system = ActorSystem("KV")
        val serverRefs = serverList.map(x => system.actorOf(Props(new Server(x, metaData)), name = x))
        serverRefs.foreach(ref => metaData.addHost(Host(ref.path.name), ref))
        serverRefs.foreach(ref => ref ! UpdateConfiguration(metaData))

        val testKey = "alice"
        val testVal = "apple"
        val target = Server.redirectToCoordinator(metaData, testKey)
        target.get ! Put(testKey, testVal, Version())
        // wait for replication consistent
        Thread.sleep(2000)

        val client = system.actorOf(Props(new Actor {
            var count = 0
            override def receive: Receive = {
                case GetResult(key, Some(value), _) =>
                    count += 1
                case GetResult(key, None, _) =>
                case "end" =>
                    println(count) // must be 3
                    assert(count == 3)
            }

            override def preStart(): Unit = {
                serverRefs.foreach(ref => ref ! PeekStorage(testKey))
            }
        }))

        Thread.sleep(2000)
        client ! "end"
        system.terminate()
    }

}
