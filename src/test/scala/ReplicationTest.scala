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

        val target = serverRefs(serverList.indexOf(metaData.partition.getServer("hello").get.address))
//        target ! Put("hello", "123")
        // wait for replication consistent
        Thread.sleep(2000)

//        val client = system.actorOf(Props(new Actor {
//            var count = 0
//            override def receive: Receive = {
//                case Result(key, Some(value)) =>
//                    assertEquals(value, "123")
//                    count += 1
//                case Result(key, None) =>
//                case "end" =>
//                    println(count)
//            }
//
//            override def preStart(): Unit = {
//                serverRefs.foreach(ref => ref ! Get("hello"))
//            }
//        }))
//
//        Thread.sleep(2000)
//        client ! "end"
        system.terminate()
    }

}
