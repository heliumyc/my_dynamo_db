package components

import myutils.HashUtil

import scala.collection.SortedMap
import scala.collection.immutable.TreeMap

/**
 * yo, homie, this is partition, aka virtual node
 */
class PartitionLayer(val virtualNums:Int = 1000) {

    private val SPLITTER = "##V"

    // one node is expanded to 1000 virtual node on the ring
//    private var virtualNums = 1000

    // node identifiers
    private var realNodes: Set[String] = Set()

    // hash to node identifier
    private var virtualNodes: SortedMap[Int, String] = TreeMap()

    def addNode(node: String): Unit = {
        realNodes += node
        refreshHashRing()
    }

    def addNodes(nodes: List[String]): Unit = {
        realNodes = realNodes ++ nodes
        refreshHashRing()
    }

    def removeNode(node: String): Unit = {
        realNodes -= node
        refreshHashRing()
    }

    def removeNode(node: List[String]): Unit = {
        realNodes --= node
        refreshHashRing()
    }

    def refreshHashRing(): Unit = {
        virtualNodes = TreeMap()
        realNodes.map(node => {
            (1 to virtualNums).map(idx => {
                val hash = HashUtil.getHash(getVirtualNodeName(node, idx))
                virtualNodes += (hash -> node)
            })
        })
    }

    /**
     * invalidate partition layer, clear everything
     */
    def clear(): Unit = {
        realNodes = Set()
        refreshHashRing()
    }

    def getServer(key: String): Option[String] = {
        if (realNodes.isEmpty || virtualNodes.isEmpty) {
            return None
        }
        val hash = HashUtil.getHash(key)
        // get key that is larger or equal
        val subMap = virtualNodes.from(hash)
        val virtualNodeName = if (subMap.isEmpty) {
            // if empty, means this is larger than the end of hash list, next is wrapped to the front
            virtualNodes(virtualNodes.firstKey)
        } else {
            // there must be value
            virtualNodes(subMap.firstKey)
        }
        Some(getRealNodeName(virtualNodeName))
    }

    private def getVirtualNodeName(realNodeName: String, virtualNodeIndex: Int): String = {
        realNodeName + SPLITTER + virtualNodeIndex
    }

    private def getRealNodeName(virtualNodeName: String): String = {
        // if split fails, raise error
        // assert this can not be None
        virtualNodeName.split(SPLITTER).head
    }

}
