package components

import akka.actor.ActorRef
import akka.util.Timeout
import components.PhysicalNode.{Get, Put, ReplicationRequest, ReplicationResponse, Result, UpdateConfiguration}
import environment.{EmulatedActor, Logged}
import myutils.CollectionUtils

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/**
 * name can be ip or something
 *
 * @param name node identifier
 */
class PhysicalNode(val name: String, val initMetadata: Metadata) extends EmulatedActor with Logged {

    val storage: mutable.HashMap[String, Record] = mutable.HashMap()

    // this avoid potential memory sharing between actors, cuz real life machines memories are isolated
    var metadata: Metadata = initMetadata.copy()

    var replicationRetryMap: Map[String, List[String]] = Map[String, List[String]]()

    def broadcast[T](targets: Iterable[String], message: T): Unit = {
        targets.flatMap(metadata.getActorRef).foreach(_ ! message)
    }

    def replicateData(key: String, value: Record): Unit = {
        // find next N
        val replicas = metadata.partition.getNextPhysicalServers(key, metadata.replicaN)
        val otherReplicas = CollectionUtils.removeElement(replicas, name)
        replicationRetryMap += (key -> (CollectionUtils.removeElement(otherReplicas, name)))
        broadcast(otherReplicas, ReplicationRequest(key, value))
    }

    override protected def receiveMsg: Receive = {
        case Put(key, value) =>
            val record = Record(value, System.currentTimeMillis())
            storage.put(key, record)
            replicateData(key, record)
        case Get(key) =>
            val msg = storage.get(key) match {
                case Some(Record(v, _)) => Result(key, Some(v))
                case None => Result(key, None)
            }
            sender() ! msg
        case ReplicationRequest(key, record) =>
            // write replication into current storage
            storage.put(key, record)
            sender() ! ReplicationResponse(success = true, name, key)
        case ReplicationResponse(true, from, key) =>
            // successful logic, remove that one
            // unsuccessful ones will be retried when timer is up
            var to_send = replicationRetryMap.get(key)
            to_send = to_send.flatMap(nodes => Some(nodes.filterNot(_ == from)))
            if (to_send.nonEmpty && to_send.get.nonEmpty) {
                replicationRetryMap = replicationRetryMap.updated(key, to_send.get)
            }
        case UpdateConfiguration(metadata) => this.metadata = metadata
    }
}

object PhysicalNode {

    case class Put(key: String, value: String)

    case class Get(key: String)

    case class Result(key: String, value: Option[String])

    case class ReplicationRequest(key: String, value: Record)

    case class ReplicationResponse(success: Boolean, from: String, key: String)

    case class UpdateConfiguration(metadata: Metadata)

    case class OK()

}
