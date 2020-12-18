package components

import components.Version.Version
import environment.Delay

trait Message

object Message {
    type Key = String
    type Value = String

    case class Put(key: Key, value: Value, version: Version) extends Message
    case class Get(key: Key, extraInfo: ExtraInfo = ExtraInfo()) extends Message
    case class GetResult(key: Key, value: Option[Record], allReplicas: List[Option[Record]] = List()) extends Message
    case class PutResult(key: Key, success: Boolean) extends Message
    case class Failure() extends Message

    case class ExtraInfo(showAllReplicas: Boolean = false)

    // between coordinator and replicas
    case class ReadReplicaRequest(queryId: Int, key: Key) extends Message
    case class ReadReplicaResponse(queryId: Int, key: Key, value: Option[Record]) extends Message

    case class WriteReplicaRequest(queryId: Int, key: Key, value: Record) extends Message
    case class WriteReplicaResponse(queryId: Int, success: Boolean, from: Host, key: Key) extends Message

    case class HintsTransitRequest(hintId: Int, key: Key, originalHost: Host, value: Record) extends Message
    case class HintedHandoffRequest(hintId: Int, key: Key, value: Record) extends Message
    case class HintedHandoffResponse(hintId: Int, success: Boolean, from: Host, key: Key) extends Message

    case class RepairRequest(key: Key, value: Record) extends Message

    case class UpdateConfiguration(metadata: Metadata) extends Message

    /************** Test Utility Message *************/
    case class PeekStorage(key: Key) extends Message
    case class UpdateDelay(delay: Delay) extends Message
    case object OK extends Message
}
