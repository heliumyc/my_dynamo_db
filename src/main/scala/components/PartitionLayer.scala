package components

import myutils.HashUtil

import scala.collection.SortedMap
import scala.collection.immutable.TreeMap

/**
 * yo, homie, this is partition, aka virtual node
 */
class PartitionLayer(val virtualNums:Int = 1000) {

    /**
     * splitter used to join real node and virtual node index
     */
    private val SPLITTER = "##V"

    /**
     * real nodes list, but only store node identifiers
     */
    private var realNodes: Set[String] = Set()

    /**
     * virtual nodes mapper, hash to virtual node identifier
     */
    private var virtualNodes: SortedMap[Int, String] = TreeMap()

    /**
     * add a node
     * @param node node id
     */
    def addNode(node: String): Unit = {
        realNodes += node
        refreshHashRing()
    }

    /**
     * add a batch of nodes
     * @param nodes node list to be added
     */
    def addNodes(nodes: List[String]): Unit = {
        realNodes = realNodes ++ nodes
        refreshHashRing()
    }

    /**
     * remove a node
     * @param node node to be removed
     */
    def removeNode(node: String): Unit = {
        realNodes -= node
        refreshHashRing()
    }

    /**
     * remove a batch of nodes
     * @param node nodes list to be removed
     */
    def removeNode(node: List[String]): Unit = {
        realNodes --= node
        refreshHashRing()
    }

    /**
     * refresh hash ring, rebuild mapping
     * theoretically, previous mapping would stay the same
     * so hashing can be consistent
     */
    def refreshHashRing(): Unit = {
        virtualNodes = TreeMap()
        realNodes.map(node => {
            (1 to virtualNums).map(idx => {
                val virtualNodeName = getVirtualNodeName(node, idx)
                val hash = HashUtil.getHash(virtualNodeName)
                virtualNodes += (hash -> virtualNodeName)
            })
        })
    }

    /**
     * invalidate partition layer, clear everything, including servers already presented
     */
    def clear(): Unit = {
        realNodes = Set()
        refreshHashRing()
    }

    /**
     * get a server identifier from registered partition based on key
     * @param key key to query
     * @return
     */
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

    def getAllPreKRange(node: String, K: Int): List[PartitionLayer.Range] = {
        (1 to virtualNums).flatMap(idx => {
            val virtualNodeName = getVirtualNodeName(node, idx)
            val hash = HashUtil.getHash(virtualNodeName)
            getPreKRange(hash, K)
        }).toList
    }

    private def getPreKRange(virtualNodeIndex: Int, K: Int): List[PartitionLayer.Range] = {
        val reversePreKeyList = virtualNodes.keySet.to(virtualNodeIndex).toList.reverse.map(a => Option(a))
        val reverseSucKeyList = virtualNodes.keySet.from(virtualNodeIndex).toList.reverse.map(a => Option(a))

        val preRangeList = if (reversePreKeyList.size >= K + 2) {
            reversePreKeyList
              .drop(1)
              .zipAll(reversePreKeyList, None, None)
              .map(x => PartitionLayer.Range(x._1, x._2))
              .take(K + 1)
        } else {
            List(PartitionLayer.Range(None, Option(virtualNodeIndex)))
        }
        val remainKeyCount = K - reversePreKeyList.size + 1
        val sucRangeList = if (reverseSucKeyList.size >= remainKeyCount + 2) {
            reverseSucKeyList
              .zipAll(reverseSucKeyList.drop(1), None, None)
              .map(x => PartitionLayer.Range(x._1, x._2))
              .take(remainKeyCount + 1)
        } else {
            List(PartitionLayer.Range(Option(virtualNodeIndex), None))
        }
        preRangeList ++ sucRangeList
    }

    /**
     * concatenate real node with virtual id, simple but effective way for this purpose
     * @param realNodeName real node name
     * @param virtualNodeIndex virtual node index (within the range of (0, virtualNums)
     * @return virtual node name
     */
    private def getVirtualNodeName(realNodeName: String, virtualNodeIndex: Int): String = {
        realNodeName + SPLITTER + virtualNodeIndex
    }

    /**
     * get real node name from virtual node name, simply split it will do the trick
     * even if splitter is in the real server node, because we split reversely
     * @param virtualNodeName virtual node name
     * @return real node name
     */
    private def getRealNodeName(virtualNodeName: String): String = {
        virtualNodeName.splitAt(virtualNodeName.lastIndexOf(SPLITTER))._1
    }
}

object PartitionLayer {

    /**
     * A index range [from, to)
     */
    case class Range(from: Option[Int], to: Option[Int]) {}
}
