package components

import myutils.HashUtil

import scala.collection.SortedMap
import scala.collection.immutable.{ListSet, TreeMap}

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
    def addNodes(nodes: Iterable[String]): Unit = {
        realNodes ++= nodes
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
    def removeNode(node: Iterable[String]): Unit = {
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

    /**
     * get unique n PHYSICAL servers from given key
     * @param key given key
     * @param count replication number
     * @return
     */
    def getNextPhysicalServers(key: String, count: Int): List[String] = {
        var max_count = Math.min(count, realNodes.size)
        var nextSet = Set[String]()
        val addFunc = (it: Iterator[(Int, String)]) => {
            while (max_count > 0 && it.hasNext) {
                val ele = getRealNodeName(it.next()._2)
                if (!nextSet.contains(ele)) {
                    nextSet += ele
                    max_count -= 1
                }
            }
        }
        addFunc(virtualNodes.iteratorFrom(HashUtil.getHash(key)))
        addFunc(virtualNodes.iterator)
        nextSet.toList
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
