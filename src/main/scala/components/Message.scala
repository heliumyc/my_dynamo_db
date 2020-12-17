package components

import components.Version.Version

trait Message

object Message {
    type Key = String
    type Value = Set[String]

    case class Put(key: Key, value: Value, version: Version) extends Message
    case class Get(key: Key) extends Message
    case class Result(key: Key, value: Option[Record]) extends Message

    // between coordinator and replicas
    case class ReadReplicaRequest(queryId: Int, key: Key) extends Message
    case class ReadReplicaResponse(queryId: Int, key: Key, value: Option[Record]) extends Message

    case class WriteReplicaRequest(requestId: Int, key: Key, value: Record) extends Message
    case class WriteReplicaResponse(requestId: Int, success: Boolean, from: Host, key: Key) extends Message

    case class HintedHandoffRequest(requestId: Int, key: Key, originalHost: Host, value: Record) extends Message
    case class HintedHandoffResponse(requestId: Int, success: Boolean, from: Host) extends Message

    case class UpdateConfiguration(metadata: Metadata) extends Message

    case class OK() extends Message
}
