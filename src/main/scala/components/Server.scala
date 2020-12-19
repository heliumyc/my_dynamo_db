package components

import components.Message._
import myutils.{CollectionUtils, IndexedBuffer}
import akka.actor.{Actor, ActorRef, Timers}
import environment.{FuzzParams, Fuzzed, MessageLogging}
import myutils.Order.{AFTER, BEFORE, CONCURRENT, SAME}
import myutils.{SimpleLogger => Logger}

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
    val hintsStorage: HintsStorage[(Host, Key, Record)] = HintsStorage()

    /**
     * this avoid potential memory sharing between actors, cuz real life machines memories are isolated
     */
    var metadata: Metadata = initMetadata.copy()

    var fuzzParams: FuzzParams = FuzzParams()

    /**
     * timers
     */
    case object HintedHandoffTimer

    case class HintsTransitTimer(queryId: QueryId, key: Key)

    case class MaxWaitTimeout(queryId: QueryId)

    val MAX_WAIT_TIMEOUT: FiniteDuration = 500.milliseconds
    val HINTS_WRITE_WAIT_TIME: FiniteDuration = 50.milliseconds
    val HANDOFF_INTERVAL_TIME: FiniteDuration = 100.milliseconds

    timers.startTimerWithFixedDelay(HintedHandoffTimer, HintedHandoffTimer, HANDOFF_INTERVAL_TIME)

    /**
     * record replication entry that waits for response
     * key -> target host list
     */
    val replicationSendBuffer: IndexedBuffer[QueryId, (Host, Record)] = IndexedBuffer()

    var clientConnectionPool: ClientConnectionPool = ClientConnectionPool()
    val readResultBuffer: IndexedBuffer[QueryId, Option[Record]] = IndexedBuffer()
    val writeResultBuffer: IndexedBuffer[QueryId, Host] = IndexedBuffer()

    /** currently is if show all replicas */
    var queryExtraInformation: Map[QueryId, ExtraInfo] = Map()

    /** ******* utility function ******** */
    def broadcast[T](targets: Iterable[Host], message: T, delay: Double, dropRate: Double): Unit = {
//        targets.flatMap(metadata.getActorRef).foreach(send(_, message, delay, dropRate))
        targets.flatMap(metadata.getActorRef).foreach(x => {
            send(x, message, delay, dropRate)
        })
    }

    def sendToHost[T](target: Host, message: T, delay: Double, dropRate: Double): Unit = {
        metadata.getActorRef(target) match {
            case Some(addr) => send(addr, message, delay, dropRate)
            case None =>
        }
    }

    def replyToClient(queryId: QueryId, message: Message): Unit = {
        val client = clientConnectionPool.popClient(queryId)
        if (client.isDefined) {
            client.get ! message
        }
    }

    def getReplicasList(key: Key): List[Host] = {
        val totalReplicas: List[Host] = metadata.partition.getNextNHosts(key, metadata.replicaN)
        val ret = CollectionUtils.removeElement(totalReplicas, currentHost)
        ret
    }

    def setMaxWaitTimeout(queryId: QueryId, delay: FiniteDuration): Unit = {
        timers.startSingleTimer(MaxWaitTimeout(queryId), MaxWaitTimeout(queryId), delay)
    }

    def cancelMaxWaitTimeout(queryId: QueryId): Unit = {
        timers.cancel(MaxWaitTimeout(queryId))
    }

    def setHintedWriteTimeout(queryId: QueryId, key: Key, delay: FiniteDuration): Unit = {
        timers.startSingleTimer(HintsTransitTimer(queryId, key), HintsTransitTimer(queryId, key), delay)
    }

    def tryMergeRecord(r1: Record, r2: Record): Record = {
        r1.version compare r2.version match {
            case BEFORE => r2
            case AFTER => r1
            case CONCURRENT => conflictResolveFunc(r1, r2)
            case SAME => r1
        }
    }

    def tryMergeAndPut(key: Key, newRecord: Record): Unit = {
        val merged = storage.get(key) match {
            case Some(oldRecord) => tryMergeRecord(oldRecord, newRecord)
            case None => newRecord
        }
        storage.put(key, merged)
    }

    def isStale(currentRecordOption: Option[Record], recvRecordOption: Option[Record]): Boolean = {
        (currentRecordOption, recvRecordOption) match {
            case (Some(curVal), Some(recVal)) => (curVal.version compare recVal.version) == AFTER
            case (None, Some(_)) => false
            case (Some(_), None) => true
            case (None, None) => false
        }
    }

    def tryReplyReadToClient(queryId: QueryId, key: Key, extraInfo: ExtraInfo): Unit = {
        // check if read quorum is achieved
        val readResults = readResultBuffer.get(queryId)
        if (readResults.length >= Math.min(metadata.quorumR, metadata.replicaN)) {
            // that's great, read is successful and we try to merge them together
            readResultBuffer.remove(queryId)
            queryExtraInformation -= queryId
            // NOTE THIS IS A PERFORMANCE PITFALL, there is an implicit conversion from Option to Iterable
            var finalRecord = readResults.reduce((acc, r) => (acc ++ r).reduceOption(tryMergeRecord))
            finalRecord = finalRecord.flatMap(r => Some(r.updateVersion(r.version.increase(currentHost))))
            // cancel read failure timeout
            cancelMaxWaitTimeout(queryId)
            // reply to client
            val allReplicas = if (extraInfo.showAllReplicas) readResults else List()
            replyToClient(queryId, GetResult(key, finalRecord, allReplicas))
        }
    }

    def tryReplyWriteToClient(queryId: QueryId, key: Key): Unit = {
        // check if write quorum is achieved
        val writeResults = writeResultBuffer.get(queryId)
        if (writeResults.length >= Math.min(metadata.quorumW, metadata.replicaN)) {
            writeResultBuffer.remove(queryId)
            // cancel write failure timeout
            cancelMaxWaitTimeout(queryId)
            // reply to client
            replyToClient(queryId, PutResult(key, success = true))
        }
    }

    /** ******* handlers ******** */
    def handleReadReplica: Receive = {
        case ReadReplicaRequest(queryId, key) =>
            val recordOption = storage.get(key)
            // read response
            send(sender(), ReadReplicaResponse(queryId, key, recordOption), fuzzParams.arsDelay, fuzzParams.dropRate)
        case ReadReplicaResponse(queryId, key, record) =>
            // receive read from other replicas, add to buffer
            if (readResultBuffer.exists(queryId)) {
                readResultBuffer.add(queryId, record)
            }
            // check if we need to do read repair
            val curVal = storage.get(key)
            if (metadata.enableReadRepair && isStale(curVal, record)) {
                // do read repair
                send(sender(), RepairRequest(key, curVal.get), fuzzParams.writeDelay, fuzzParams.dropRate)
            }
            tryReplyReadToClient(queryId, key, queryExtraInformation.getOrElse(queryId, ExtraInfo()))
    }

    def handleWriteReplica: Receive = {
        case WriteReplicaRequest(queryId, key, record) =>
            // write replication into current storage
            tryMergeAndPut(key, record)
            // write ack
            send(sender(), WriteReplicaResponse(queryId, success = true, currentHost, key), fuzzParams.arsDelay, fuzzParams.dropRate)
        case WriteReplicaResponse(queryId, true, from, key) =>
            // successful logic, remove it from to_send list
            // unsuccessful ones will be retried when timer is up
            replicationSendBuffer.remove(queryId, _._1 == from)
            tryReplyWriteToClient(queryId, key)
        case WriteReplicaResponse(queryId, false, from, _) =>
    }

    def handleReadRepair: Receive = {
        case RepairRequest(key, record) => tryMergeAndPut(key, record)
    }

    def handleHintedHandoff: Receive = {
        case HintsTransitRequest(queryId, key, originalHost, record) =>
            hintsStorage.add((originalHost, key, record))
            send(sender(), WriteReplicaResponse(queryId, success = true, currentHost, key), fuzzParams.arsDelay, fuzzParams.dropRate)
        case HintedHandoffRequest(hintId, key, record) =>
            // write replication into current storage
            tryMergeAndPut(key, record)
            send(sender(), HintedHandoffResponse(hintId, success = true, currentHost, key), fuzzParams.arsDelay, fuzzParams.dropRate)
        case HintedHandoffResponse(hintId, true, from, key) =>
            // successful logic, remove it from to_send list
            // unsuccessful ones will be retried when timer is up
            hintsStorage.remove(hintId)
        case HintedHandoffResponse(hintId, false, from, key) =>
        case HintsTransitTimer(queryId, key) =>
            // for those who are still in wait list, send them to somewhere else as hints storage
            val replicasToHandoff = replicationSendBuffer.get(queryId)
            val hintsNodes = metadata.partition.getNextNHosts(key, replicasToHandoff.length, metadata.replicaN)
            (replicasToHandoff zip hintsNodes).foreach {
                case ((host, record), target) =>
                    sendToHost(target, HintsTransitRequest(queryId, key, host, record), fuzzParams.arsDelay, fuzzParams.dropRate)
            }
        case HintedHandoffTimer =>
            // scan hints storage and handoff them to original host
            hintsStorage.internalData.foreach {
                case (hintId, (host, key, record)) =>
                    sendToHost(host, HintedHandoffRequest(hintId, key, record), fuzzParams.arsDelay, fuzzParams.dropRate)
            }
    }

    def handleQuery: Receive = {
        case Put(key, value, version) =>
            // get unique query id
            val queryId = clientConnectionPool.addConnection(sender())
            // update version
            val updatedVersion = version.increase(currentHost)
            // store record into memory
            val record = Record(value, updatedVersion)
            storage.put(key, record)
            writeResultBuffer.add(queryId, currentHost)
            // replicate data to other replication server
            val replicas = getReplicasList(key)
            replicationSendBuffer.set(queryId, replicas.map(host => (host, record)))
            broadcast(replicas, WriteReplicaRequest(queryId, key, record), fuzzParams.writeDelay, fuzzParams.dropRate)
            // set timeout, if it is up, then this query fails
            setMaxWaitTimeout(queryId, MAX_WAIT_TIMEOUT)
            // set timeout for hinted handoff, if it is up, then find a node to store hints
            setHintedWriteTimeout(queryId, key, HINTS_WRITE_WAIT_TIME)
            tryReplyWriteToClient(queryId, key)
        case Get(key, extraInfo) =>
            // get unique query id
            val queryId = clientConnectionPool.addConnection(sender())
            // fetch record from current machine
            val record = storage.get(key)
            // store into buffer for client connection
            readResultBuffer.add(queryId, record)
            // fetch record from other replication
            val replicas = getReplicasList(key)
            broadcast(replicas, ReadReplicaRequest(queryId, key), fuzzParams.arsDelay, fuzzParams.dropRate)
            // set timeout, if it is up, then this query fails
            setMaxWaitTimeout(queryId, MAX_WAIT_TIMEOUT)
            tryReplyReadToClient(queryId, key, extraInfo)
            queryExtraInformation += (queryId -> extraInfo)
    }


    def handleTimeout: Receive = {
        case MaxWaitTimeout(queryId: QueryId) =>
            // stale data or other corner case
            if (replicationSendBuffer.exists(queryId) && readResultBuffer.exists(queryId) && writeResultBuffer.exists(queryId)) {
                // clear this query from every buffer, make stale object GC
                replicationSendBuffer.remove(queryId)
                readResultBuffer.remove(queryId)
                writeResultBuffer.remove(queryId)
                // this is quite unlikely to happen and is what we try hard to prevent
                replyToClient(queryId, Failure())
            }
    }

    def handleConfiguration: Receive = {
        case UpdateConfiguration(metadata) => this.metadata = metadata.copy()
    }

    case object TestTimer
//    timers.startTimerWithFixedDelay(TestTimer, TestTimer, 500.milliseconds)

    /**
     * only for test
     * @return
     */
    def handleTestKit: Receive = {
        case PeekStorage(key) =>
            sender() ! GetResult(key, storage.get(key))
        case UpdateFuzzParams(fuzzParams) =>
            this.fuzzParams = fuzzParams
//        case TestTimer =>
//            Logger.info(context.system.eventStream.toString)
    }

    override def receive: Receive = List(
        handleQuery, handleReadReplica, handleWriteReplica, handleHintedHandoff, handleReadRepair,
        handleConfiguration, handleTimeout, handleTestKit
    ).reduce(_ orElse _)
}

object Server {
    def redirectToCoordinator(metadata: Metadata, key: Key): Option[ActorRef] = {
        val host = metadata.partition.getServer(key).get
        metadata.getActorRef(host)
    }
}
