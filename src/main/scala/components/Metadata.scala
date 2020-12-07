package components

import akka.actor.ActorRef

/**
 * every physical node should keep one
 * and initialized when node is bootstrapping
 *
 * @param quorumW, write minimum
 * @param quorumR, read minimum
 * @param replicaN, must > 0
 */
class Metadata(val quorumW: Int, val quorumR: Int, val replicaN: Int) {

    val partition: PartitionLayer = new PartitionLayer(1000)

    private var physicalNodeSet: Set[String] = Set()

    /**
     * this is necessary!!!
     * if we look up from context.system
     * we would know if a node is down or not, this violate our purpose
     * we don't have a oracle machine to check node status
     */
    private var referenceMap: Map[String, ActorRef] = Map()

    def addPhysicalNode(name: String, ref: ActorRef): Unit = {
        if (!physicalNodeSet.contains(name)) {
            partition.addNode(name)
            physicalNodeSet += name
            referenceMap += (name -> ref)
        }
    }

    def removePhysicalNode(name: String): Unit = {
        partition.removeNode(name)
        physicalNodeSet -= name
        referenceMap -= name
    }

    def copy(): Metadata = {
        val ret = new Metadata(quorumW, quorumR, replicaN)
        ret.partition.addNodes(this.physicalNodeSet)
        // they are immutable, so shallow copying is perfectly fine
        ret.physicalNodeSet = this.physicalNodeSet
        ret.referenceMap = this.referenceMap
        ret
    }

    def getActorRef(server: String): Option[ActorRef] = referenceMap.get(server)

}
