package components

import myutils.HashUtil

import scala.collection.SortedMap
import scala.collection.immutable.{ListSet, TreeMap}

/**
 * partitioner
 */
class Partitioner(val virtualNums:Int = 512) {

    /**
     * splitter used to join real node and virtual node index
     */
    private val SPLITTER = "##V"

    /**
     * host list
     */
    private var hosts: Set[Host] = Set()

    /**
     * virtual nodes mapper, hash to virtual node identifier
     */
    private var vnodes: SortedMap[Int, Vnode] = TreeMap()

    /**
     * from vhost to host
     */
    private var vnodesToHostMap: Map[Vnode, Host] = Map()

    /**
     * add a node
     * @param host node id
     */
    def addHost(host: Host): Unit = {
        this.hosts += host
        refreshHashRing()
    }

    /**
     * add a batch of nodes
     * @param hosts node list to be added
     */
    def addHosts(hosts: Iterable[Host]): Unit = {
        this.hosts ++= hosts
        refreshHashRing()
    }

    /**
     * remove a node
     * @param host node to be removed
     */
    def removeNode(host: Host): Unit = {
        hosts -= host
        refreshHashRing()
    }

    /**
     * remove a batch of nodes
     * @param hosts nodes list to be removed
     */
    def removeNode(hosts: Iterable[Host]): Unit = {
        this.hosts --= hosts
        refreshHashRing()
    }

    /**
     * refresh hash ring, rebuild mapping
     * theoretically, previous mapping would stay the same
     * so hashing can be consistent
     */
    def refreshHashRing(): Unit = {
        vnodes = TreeMap()
        hosts.map(node => {
            (1 to virtualNums).map(idx => {
                val vnode = getVnode(node, idx)
                vnodes += (vnode.position -> vnode)
            })
        })
    }

    /**
     * invalidate partition layer, clear everything, including servers already presented
     */
    def clear(): Unit = {
        hosts = Set()
        refreshHashRing()
    }

    /**
     * get a server identifier from registered partition based on key
     * @param key key to query
     * @return
     */
    def getServer(key: String): Option[Host] = {
        if (hosts.isEmpty || vnodes.isEmpty) {
            return None
        }
        val hash = HashUtil.getHash(key)
        // get key that is larger or equal
        val subMap = vnodes.from(hash)
        val vnode = if (subMap.isEmpty) {
            // if empty, means this is larger than the end of hash list, next is wrapped to the front
            vnodes(vnodes.firstKey)
        } else {
            // there must be value
            vnodes(subMap.firstKey)
        }
        Some(getHost(vnode))
    }

    /**
     * get unique n PHYSICAL servers from given key
     * @param key given key
     * @param count replication number
     * @return
     */
    def getNextNHosts(key: String, count: Int): List[Host] = {
        var max_count = Math.min(count, hosts.size)
        var nextSet = Set[Host]()
        val addFunc = (it: Iterator[(Int, Vnode)]) => {
            while (max_count > 0 && it.hasNext) {
                val ele = getHost(it.next()._2)
                if (!nextSet.contains(ele)) {
                    nextSet += ele
                    max_count -= 1
                }
            }
        }
        addFunc(vnodes.iteratorFrom(HashUtil.getHash(key)))
        addFunc(vnodes.iterator)
        nextSet.toList
    }

    def getNextNHosts(key: String, count: Int, skip: Int): List[Host] = {
        (getNextNHosts(key, count+skip).toSet diff getNextNHosts(key, count).toSet).toList
    }

    /**
     * concatenate real node with virtual id, simple but effective way for this purpose
     * @param host host
     * @param vnodeIdx virtual node index (within the range of (0, virtualNums)
     * @return virtual node
     */
    private def getVnode(host: Host, vnodeIdx: Int): Vnode = {
        val name = host.address + SPLITTER + vnodeIdx
        Vnode(name, HashUtil.getHash(name))
    }

    /**
     * get real node name from virtual node name, simply split it will do the trick
     * even if splitter is in the real server node, because we split reversely
     * @param vnode virtual node
     * @return host
     */
    private def getHost(vnode: Vnode): Host = {
        Host(vnode.name.splitAt(vnode.name.lastIndexOf(SPLITTER))._1)
    }

}
