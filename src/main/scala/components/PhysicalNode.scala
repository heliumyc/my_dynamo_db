package components

import akka.actor.Props
import components.PhysicalNode.{Get, Put, Result}
import environment.{EmulatedActor, Logged}

import scala.collection.mutable

/**
 * name can be ip or something
 * @param name node identifier
 */
class PhysicalNode(val name: String, var config: Configuration) extends EmulatedActor with Logged {

    val storage: mutable.SortedMap[String, String] = mutable.TreeMap()

    override protected def receiveMsg: Receive = {
        case Put(key,value) => storage.put(key, value)
        case Get(key) =>
            val msg = storage.get(key) match {
                case Some(v) => Result(key, Some(v))
                case None => Result(key, None)
            }
            sender() ! msg
    }

    override def preStart(): Unit = {
        super.preStart()
        context.actorOf(
            Props(new GossipActor(config, mutable.Set[String](), mutable.Set[String]())),
            name = "Gossip")
    }
}

object PhysicalNode {

    case class Put(key: String, value: String)

    case class Get(key: String)

    case class Result(key: String, value: Option[String])

    case class OK()
}
