package components

import components.Message._
import myutils.{CollectionUtils, IndexedBuffer}
import akka.actor.{Actor, Timers}
import environment.{Fuzzed, MessageLogging}
import myutils.Order.{AFTER, BEFORE, CONCURRENT}

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * name can be ip or something
 * interesting enough, Timers trait MUST be in front of customized pipelines or errors are raised
 *
 * @param name node identifier
 */
class Server(val name: String,
             val initMetadata: Metadata,
             val conflictResolveFunc: (Record, Record) => Record = ConflictResolvePolicies.lastWriteWins)
    extends Actor with Timers with MessageLogging with Fuzzed {

    type Key = String
    type QueryId = Int
    type HintId = Int

    val currentHost: Host = Host(name)

    val storage: mutable.HashMap[Key, Record] = mutable.HashMap()

    /**
     * storage only for hinted handoff
     * hintsIdx -> (host, key, value)
     */
    val hintsStorage: mutable.HashMap[HintId, (Host, Key, Record)] = mutable.HashMap()

    /**
     * this avoid potential memory sharing between actors, cuz real life machines memories are isolated
     */
    var metadata: Metadata = initMetadata.copy()

    /**
     * timers
     */
    case object HintedHandoffTimer

    case class Timeout(operationId: QueryId)

    val RECEIVE_TIMEOUT: FiniteDuration = (20 milliseconds)
    timers.startTimerWithFixedDelay(HintedHandoffTimer, HintedHandoffTimer, 3.seconds)

    /**
     * record replication entry that waits for response
     * key -> target host list
     */
    var replicationSendBuffer: IndexedBuffer[QueryId, Host] = IndexedBuffer()

    var clientConnectionPool: ClientConnectionPool = ClientConnectionPool()
    var queryResultBuffer: IndexedBuffer[QueryId, Option[Record]] = IndexedBuffer()

    /** ******* utility function ******** */
    def broadcast[T](targets: Iterable[Host], message: T): Unit = {
        targets.flatMap(metadata.getActorRef).foreach(send(_, message))
    }

    def getReplicasList(key: Key): List[Host] = {
        val totalReplicas: List[Host] = metadata.partition.getNextNHosts(key, metadata.replicaN)
        CollectionUtils.removeElement(totalReplicas, currentHost)
    }

    def replicateData(queryId: QueryId, key: Key, value: Record): Unit = {
        // find next N
        val replicas = getReplicasList(key)
        replicationSendBuffer.set(queryId, replicas)
        broadcast(replicas, WriteReplicaRequest(queryId, key, value))
        // set replication timeout
        setTimeout(queryId, RECEIVE_TIMEOUT)
    }

    def setTimeout(queryId: QueryId, delay: FiniteDuration): Unit = {
        timers.startSingleTimer(Timeout(queryId), Timeout(queryId), delay)
    }

    def tryMergeRecord(r1: Record, r2: Record): Record = {
        r1.version compare r2.version match {
            case BEFORE => r2
            case AFTER => r1
            case CONCURRENT => conflictResolveFunc(r1, r2)
        }
    }

    def tryReplyReadToClient(queryId: QueryId, key: Key): Unit = {
        // check if read quorum is achieved
        val readResults = queryResultBuffer.get(queryId)
        if (readResults.length >= metadata.quorumR) {
            // that's great, read is successful and we try to merge them together
            queryResultBuffer.remove(queryId)
            // NOTE THIS IS A PERFORMANCE PITFALL, there is an implicit conversion from Option to Iterable
            val finalRecord = readResults.reduce((acc, r) => (acc ++ r).reduceOption(tryMergeRecord))
            // cancel timeout
            // TODO
            // reply to client
            val client = clientConnectionPool.popClient(queryId)
            if (client.isDefined) {
                client.get ! Result(key, finalRecord)
            }
        }
    }

    /** ******* handlers ******** */
    def handleReadReplica: Receive = {
        case ReadReplicaRequest(queryId, key) =>
            val recordOption = storage.get(key)
            reply(ReadReplicaResponse(queryId, key, recordOption))
        case ReadReplicaResponse(queryId, key, record) =>
            // receive read from other replicas, add to buffer
            queryResultBuffer.add(queryId, record)
            tryReplyReadToClient(queryId, key)
    }

    def handleWriteReplica: Receive = {
        case WriteReplicaRequest(requestId, key, record) =>
            // write replication into current storage
            storage.put(key, record)
            reply(WriteReplicaResponse(requestId, success = true, currentHost, key))
        case WriteReplicaResponse(requestId, true, from, key) =>
            // successful logic, remove it from to_send list
            // unsuccessful ones will be retried when timer is up
            replicationSendBuffer.remove(requestId, from)
    }

    def handleHintedHandoff: Receive = {
        case HintedHandoffRequest(queryId, key, originalHost, record) =>
            hintsStorage.put(queryId, (originalHost, key, record))
            reply(WriteReplicaResponse(queryId, success = true, currentHost, key))
    }

    def handleQuery: Receive = {
        case Put(key, value, version) =>
            // get unique query id
            val queryId = clientConnectionPool.addConnection()
            // update version
            val updatedVersion = version.increase(currentHost)
            // store record into memory
            val record = Record(value, updatedVersion)
            storage.put(key, record)
            // replicate data to other replication server
            replicateData(queryId, key, record)
        case Get(key) =>
            // get unique query id
            val queryId = clientConnectionPool.addConnection()
            // fetch record from current machine
            val record = storage.get(key)
            // store into buffer for client connection
            queryResultBuffer += (queryId ->)
            // fetch record from other replication
            val replicas = getReplicasList(key)
            broadcast(replicas, ReadReplicaRequest(queryId, key))
            // set timeout, 20 is the 2 sigma of delay variance
            setTimeout(queryId, RECEIVE_TIMEOUT)
    }

    def handleConfiguration: Receive = {
        case UpdateConfiguration(metadata) => this.metadata = metadata
    }

    override def receive: Receive = handleQuery orElse handleWriteReplica orElse handleConfiguration
}

object Server {

}
