import akka.actor.{ActorSystem, Props}
import junit.framework.TestCase
import components.{Configuration, PhysicalNode, VectorClock}

class GossipTest extends TestCase{
    def testConvergence(): Unit = {
        val system = ActorSystem("KV")

        val nodes = (1 to 3) map {i =>
            "server" + i}
        val config_1 = Configuration(
            nodes.take(2).toSet,
            nodes.take(2).toSet,
            "server1",
            VectorClock(Map())
        )
        val config_2 = config_1.copy(
            vectorClock = VectorClock(Map())
        )
        val config_3 = config_2.copy(
            nodes = nodes.takeRight(2).toSet,
            preferenceList = nodes.takeRight(2).toSet,
            vectorClock = VectorClock(Map())
        )
        val configs = IndexedSeq(config_1, config_2, config_3)

        val servers = (0 to 2) map {i =>
            system.actorOf(Props(new PhysicalNode(nodes(i), configs(i))), name = nodes(i))
        }

        Thread.sleep(10000)
    }
}
