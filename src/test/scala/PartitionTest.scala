import components.PartitionLayer
import junit.framework.TestCase

import scala.collection.mutable

class PartitionTest extends TestCase {

    def emulateServer(operationTimes: Int, partition: PartitionLayer): Map[String, Int] = {
        val counter = mutable.HashMap[String, Int]()
        (1 to operationTimes).foreach(key => {
            val node = partition.getServer(key.toString).get
            counter.put(node, counter.getOrElse(node, 0) + 1)
        })
        counter.toMap
    }

    def printStatistics(counter: Map[String,Int]): Unit = {
        val total:Int = counter.foldLeft(0)((x,y) => x+y._2)
        counter.foreach { case (x,y) =>
            println(f"$x: ${y.toDouble/total}%2.2f ($y/$total)")
        }
    }

    def testGoodLoadBalance(): Unit = {
        val servers = List(
            "server 1", "server 2", "server 3"
        )

        val partition = new PartitionLayer()
        partition.addNodes(servers)

        val counter = emulateServer(10000, partition)
        println("Good load balance")
        printStatistics(counter)
    }

    def testBadLoadBalance(): Unit = {
        val servers = List(
            "server 1", "server 2", "server 3"
        )

        val partition = new PartitionLayer(5)
        partition.addNodes(servers)

        val counter = emulateServer(10000, partition)
        println("Bad load balance")
        printStatistics(counter)
    }

    def testAddServer(): Unit = {
        val servers = List(
            "server 1", "server 2", "server 3"
        )

        val partition = new PartitionLayer()
        partition.addNodes(servers)

        println("Before add server load balance")
        printStatistics(emulateServer(10000, partition))

        partition.addNode("server 4")
        partition.addNode("server 5")

        println("After add server load balance")
        printStatistics(emulateServer(10000, partition))
    }

    def testDeleteServer(): Unit = {
        val servers = List(
            "server 1", "server 2", "server 3", "server 4", "server 5", "server 6"
        )

        val partition = new PartitionLayer()
        partition.addNodes(servers)

        println("Before add server load balance")
        printStatistics(emulateServer(10000, partition))

        partition.removeNode("server 4")
        partition.removeNode("server 2")

        println("After add server load balance")
        printStatistics(emulateServer(10000, partition))
    }

}
