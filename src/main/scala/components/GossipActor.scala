package components

import akka.actor.{Actor, ActorRef, Timers}
import components.GossipActor.{AddNewMember, GetMemberShip, Membership, Push, SetMembership, SetRouter}
import environment.{Fuzzed, MessageLogging}

import scala.concurrent.duration.DurationInt

/**
 * core logic for gossip protocol
 */
class GossipActor(val current: Host) extends Actor with Timers with MessageLogging with Fuzzed {

//    val rand = new Random()
    val fanoutFactor = 1
    var activeMembers: Map[Host, Long] = Map(current->System.currentTimeMillis())
    val maxDelay: Double = 100
    var router: Map[Host, ActorRef] = Map()

    def randomPick(): List[Host] = {
        rand.shuffle(activeMembers.keys.toList).take(fanoutFactor)
    }

    case object Timer
    timers.startTimerAtFixedRate(Timer, Timer, 50.milliseconds)

    def broadcast[T](targets: Iterable[Host], message: T, delay: Double, dropRate: Double): Unit = {
        targets.flatMap(router.get).foreach(x => {
            send(x, message, delay, dropRate)
        })
    }

    def getDelay: Double = Math.abs(rand.nextDouble) * maxDelay

    def handle: Receive = {
        case Timer =>
            activeMembers += current -> System.currentTimeMillis()
            val peers = randomPick()
            broadcast(peers, Push(activeMembers), getDelay, 0.0)
        case Push(peerMembers) =>
            peerMembers.foreach{
                case (host, lastActive) =>
                    if (activeMembers.getOrElse(host, 0L) < lastActive) {
                        activeMembers += (host -> lastActive)
                    }
            }
        case AddNewMember(host) =>
            activeMembers += host -> System.currentTimeMillis()
            send(router(host), Push(activeMembers), getDelay, 0.0)
        case SetMembership(hosts, time) =>
            activeMembers = hosts.map(_ -> time).toMap
            self ! Timer
        case GetMemberShip() =>
            // client connection, no data loss
            sender() ! Membership(current, activeMembers.keys.toList)
        case SetRouter(router) =>
            this.router = router
    }

    override def receive: Receive = handle
}

object GossipActor {
    case class SetMembership(hosts: List[Host], time: Long = System.currentTimeMillis()) extends Message
    case class GetMemberShip() extends Message
    case class Heartbeat(host: Host) extends Message
    case class Push(members: Map[Host, Long]) extends Message
    case class SetRouter(router: Map[Host, ActorRef]) extends Message
    case class AddNewMember(host:Host) extends Message
    case class Membership(host: Host, membership: List[Host]) extends Message
}
