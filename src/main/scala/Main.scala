import akka.actor._
import akka.pattern.{after, ask, extended}
import akka.util.Timeout
import components.{Message, Metadata, Server}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import environment.Fuzzed

import scala.util.Random

object Main extends App {

    abstract class Message

    case class Redirect(s: String, target: ActorRef) extends Message
    case class Salute(s: String) extends Message
    case object Cancel extends Message

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

    trait TestImplicit extends Actor {
        def f(implicit s: String = "fuck"): Unit = {
            println(s)
        }
    }

    class A extends Actor with Fuzzed with Timers {

        case object TimerKey
        case object Timer

        timers.startTimerWithFixedDelay(TimerKey, Timer, 300.millis)

        implicit var x: String = "init"

        def handleMsg: Receive = {
            case Redirect(x, target) =>
                println(x)
                send(target, "fuck", 0, 0)
            case Salute(x) =>
                this.x = x
                after(1000.milliseconds)(Future{
                    sender() ! "this is set!"
                })(context.system)
            case s: String =>
                println(s"received ${s} in ${context.self} from ${sender()}")
        }

        def handleTimer: Receive = {
            case Timer =>
//                println(s"Timer is up in ${x}")
            case Cancel =>
//                println("receive cancel")
                timers.cancel(TimerKey)
        }

        override def receive: Receive = handleMsg orElse handleTimer
    }

    override def main(args: Array[String]): Unit = {
        val system = ActorSystem("KV")
        val server1 = system.actorOf(Props[A], name = "server1")
        Thread.sleep(2000)

        implicit val timeout: Timeout = Timeout(5.seconds)
        val future = server1 ? Salute("hello")
        val retString = Await.result(future, timeout.duration).asInstanceOf[String]
        println(retString)
    }

}

