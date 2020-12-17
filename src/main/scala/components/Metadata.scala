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

    val partition: Partitioner = new Partitioner(256)

    private var hostsSet: Set[Host] = Set()

    /**
     * this is necessary!!!
     * if we look up from context.system
     * we would know if a node is down or not, this violate our purpose
     * since we don't have an oracle machine to check node status
     */
    private var referenceMap: Map[Host, ActorRef] = Map()

    def addHost(host: Host, ref: ActorRef): Unit = {
        if (!hostsSet.contains(host)) {
            partition.addHost(host)
            hostsSet += host
            referenceMap += (host -> ref)
        }
    }

    def removeHost(host: Host): Unit = {
        partition.removeNode(host)
        hostsSet -= host
        referenceMap -= host
    }

    def copy(): Metadata = {
        val ret = new Metadata(quorumW, quorumR, replicaN)
        ret.partition.addHosts(this.hostsSet)
        // they are immutable, so shallow copying is perfectly fine
        ret.hostsSet = this.hostsSet
        ret.referenceMap = this.referenceMap
        ret
    }

    def getActorRef(server: Host): Option[ActorRef] = referenceMap.get(server)

}
