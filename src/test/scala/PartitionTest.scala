import components.{Host, Partitioner}
import junit.framework.TestCase

import scala.collection.mutable

class PartitionTest extends TestCase {

    def emulateServer(operationTimes: Int, partition: Partitioner): Map[String, Int] = {
        val counter = mutable.HashMap[String, Int]()
        (1 to operationTimes).foreach(key => {
            val node = partition.getServer(key.toString).get.address
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

        val partition = new Partitioner()
        partition.addHosts(servers.map(Host))

        val counter = emulateServer(10000, partition)
        println("Good load balance")
        printStatistics(counter)
    }

    def testBadLoadBalance(): Unit = {
        val servers = List(
            "server 1", "server 2", "server 3"
        )

        val partition = new Partitioner(5)
        partition.addHosts(servers.map(Host))

        val counter = emulateServer(10000, partition)
        println("Bad load balance")
        printStatistics(counter)
    }

    def testAddServer(): Unit = {
        val servers = List(
            "server 1", "server 2", "server 3"
        )

        val partition = new Partitioner()
        partition.addHosts(servers.map(Host))

        println("Before add server load balance")
        printStatistics(emulateServer(10000, partition))

        partition.addHost(Host("server 4"))
        partition.addHost(Host("server 5"))

        println("After add server load balance")
        printStatistics(emulateServer(10000, partition))
    }

    def testDeleteServer(): Unit = {
        val servers = List(
            "server 1", "server 2", "server 3", "server 4", "server 5", "server 6##"
        )

        val partition = new Partitioner()
        partition.addHosts(servers.map(Host))

        println("Before add server load balance")
        printStatistics(emulateServer(10000, partition))

        partition.removeNode(Host("server 4"))
        partition.removeNode(Host("server 2"))

        println("After add server load balance")
        printStatistics(emulateServer(10000, partition))
    }

}
