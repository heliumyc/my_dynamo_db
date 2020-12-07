package components

import akka.actor.{ActorRef, ActorSelection, Timers}
import components.GossipActor.{Discover, HeartBeat, MergeRequest, MergeResponse, Request, Response, Start, Timeout}
import environment.EmulatedActor

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

/**
 * We divide the gossip protocol into at most three steps.
 *
 * 1. send vector clock to a randomly picked neighbor
 *
 * 2. if the neighbor has newer version
 * then it sends the membership list back
 * else if neighbor has older version
 * then it requests for the membership list
 * else it requests for the membership list and attaches
 * its own membership list
 *
 * 3. if the neighbor requests for the membership list,
 * the origin node send the membership list out
 * if it attaches its membership list in the same message,
 * merge the two membership list
 * else replace its membership list by the received list.
 */
class GossipActor(var config: Configuration, val seenSet: mutable.Set[String], val failedSet: mutable.Set[String])
    extends EmulatedActor with GossipProtocol with Timers {
    val timeout = 10
    val periodicKey = "START"

    override def preStart(): Unit = {
        getActor(config.seed) ! Discover()
        timers.startTimerAtFixedRate(periodicKey, Start(), 1.second)
        seenSet.add(context.parent.path.name)
    }

    override def receiveMsg: Receive = {
        case Start() =>
            sendHeartBeat()
        case HeartBeat(clock) =>
            cancelTimerAndUpdateFailure()
            handleHeartBeat(clock)
        case Discover() =>
            handleDiscover()
        case Request() =>
            cancelTimerAndUpdateFailure()
            sender() ! Response(config)
        case MergeRequest(otherConfig) =>
            cancelTimerAndUpdateFailure()
            handleMergeRequest(otherConfig)
        case Response(otherConfig) =>
            cancelTimerAndUpdateFailure()
            handleResponse(otherConfig)
        case MergeResponse(otherConfig) =>
            cancelTimerAndUpdateFailure()
            handleMergeResponse(otherConfig)
        case Timeout(peer) =>
            failedSet.add(peer)
    }

    private def cancelTimerAndUpdateFailure(): Unit = {
        timers.cancel(sender().path)
        failedSet.remove(whoIs(sender()))
    }

    override def pickOnePeer(): Option[String] = {
        val candidates = config.nodes.diff(seenSet)
        if (candidates.nonEmpty) {
            val n = util.Random.nextInt(candidates.size)
            Option(candidates.iterator.drop(n).next())
        } else {
            None
        }
    }

    override def sendHeartBeat(): Unit = {
        val peerName = pickOnePeer()
        peerName match {
            case Some(p) =>
                val peer = getActor(p)
                peer ! HeartBeat(config.vectorClock)
                timers.startTimerWithFixedDelay(peer.pathString, Timeout, timeout.second)
            case None =>
                println(s"Converged. Membership of ${whoIs(self)} are ${config.nodes}.")
        }
    }

    override def handleHeartBeat(clock: VectorClock[String]): Unit = {
        val order = VectorClock.compare(config.vectorClock, clock)
        order match {
            case Order.after =>
                sender ! Response(config)
                seenSet.add(whoIs(sender()))
            case Order.before =>
                sender ! Request()
            case Order.concurrent =>
                sender ! MergeRequest(config)
        }
    }

    override def handleDiscover(): Unit = {
        if (!config.nodes.contains(whoIs(sender()))) {
            println(s"Get discover from ${whoIs(sender())}")
            config = config.copy(
                nodes = config.nodes + whoIs(sender()),
                vectorClock = config.vectorClock.increase(whoIs(self))
            )
        }
        sender() ! Response(config)
    }

    override def handleMergeRequest(otherConfig: Configuration): Unit = {
        val isSameMembership = handleResponse(otherConfig)
        if (!isSameMembership) {
            sender() ! MergeResponse(config)
        }
    }

    override def handleResponse(otherConfig: Configuration): Boolean = {
        val isSameMembership = config.nodes.equals(otherConfig.nodes)
        if (isSameMembership) {
            config = config.copy(
                vectorClock = VectorClock.merge(whoIs(self), config.vectorClock, otherConfig.vectorClock)
            )
            seenSet.add(whoIs(sender()))
        } else {
            config = config.copy(
                nodes = config.nodes.union(otherConfig.nodes),
                vectorClock = VectorClock.merge(whoIs(self), config.vectorClock, otherConfig.vectorClock)
            )
            // TODO:
            //  membership changed so we need to transfer some keys
            //  to the newly added nodes
            seenSet.clear()
            seenSet.add(whoIs(self))
            seenSet.add(whoIs(sender()))
        }
        isSameMembership
    }

    override def handleMergeResponse(otherConfig: Configuration): Boolean = handleResponse(otherConfig)

    private def whoIs(actor: ActorRef): String = {
        actor.path.parent.name
    }

    private def getActor(name: String): ActorSelection = {
        context.actorSelection("/user/" + name + "/Gossip")
    }
}

object GossipActor {
    case class Start()

    case class Discover()

    case class HeartBeat(vectorClock: VectorClock[String])

    case class Request()

    case class MergeRequest(config: Configuration)

    case class Response(config: Configuration)

    case class MergeResponse(config: Configuration)

    case class MembershipChanged()

    case class Timeout(peer: String)
}