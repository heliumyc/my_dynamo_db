import akka.actor._
import akka.pattern.extended
import components.{Message, Metadata, Server}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.pattern.after
import environment.Fuzzed

import scala.util.Random

object Main extends App {

    abstract class Message

    case class Salute(s: String, target: ActorRef) extends Message

    trait MyPipeline extends Actor {
        override def aroundReceive(receive: Receive, msg: Any): Unit = {
            msg match {
                case x: Message =>
                    println(x)
                case _ =>
            }
            super.aroundReceive(receive, msg)
        }
    }

    class A extends Actor with Fuzzed with Timers {

        case object TimerKey

        case object Timer

        var timercounter = 3
        timers.startTimerWithFixedDelay(TimerKey, Timer, 300.millis)

        def handleMsg: Receive = {
            case Salute(x, target) =>
                println(x)
                println(s"current gen $randGen")
                send(target, "fuck")
            case s: String =>
                println(s"received ${s} in ${context.self} from ${sender()}")
        }

        def handleTimer: Receive = {
            case Timer =>
                println(s"Timer is up in ${context.self.path.name}")
        }

        override def receive: Receive = handleMsg orElse handleTimer
    }

    override def main(args: Array[String]): Unit = {
        val system = ActorSystem("KV")
        val server1 = system.actorOf(Props[A], name = "server1")
        val server2 = system.actorOf(Props[A], name = "server2")
        server1 ! Salute("babababba", server2)
        server2 ! Salute("dididididi", server1)
    }

}

