package components

import myutils.VectorClock

import scala.collection.mutable

trait GossipProtocol {
    var config: Configuration
    val seenSet: mutable.Set[String]

    def pickOnePeer(): Option[String]

    def sendHeartBeat(): Unit

    def handleHeartBeat(vectorClock: VectorClock[String]): Unit

    def handleDiscover(): Unit

    def handleMergeRequest(otherConfig: Configuration): Unit

    def handleResponse(otherConfig: Configuration): Boolean

    def handleMergeResponse(otherConfig: Configuration): Boolean
}