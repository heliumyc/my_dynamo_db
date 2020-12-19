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

    class A extends Actor with Timers {

        case object TimerKey
        case object Timer

        timers.startTimerWithFixedDelay(TimerKey, Timer, 300.millis)

        implicit var x: String = "init"

        def handleMsg: Receive = {
            case Redirect(x, target) =>
                println(x)
//                send(target, "fuck", 0, 0)
            case Salute(x) =>
                this.x = x
//                send(self, "self", 0, 0)
                println(sender())
//                send(sender(), "tmd", 10, 0)
                val s = sender()
                after(10.milliseconds)(Future{
                    s ! "this is set!"
                })(context.system)
            case s: String =>
                sender ! "nmd"
                println(s"received ${s} in ${context.self} from ${sender()}")
        }

        def handleTimer: Receive = {
            case Timer =>
                println(s"Timer is up in ${x}")
            case Cancel =>
                println("receive cancel")
                timers.cancel(TimerKey)
        }

        override def receive: Receive = handleMsg orElse handleTimer
    }

    def test(): Unit = {
        val system = ActorSystem("KV")
        val server1 = system.actorOf(Props[A], name = "server1")
        Thread.sleep(2000)

        implicit val timeout: Timeout = Timeout(5.seconds)
//        server1 ? "fuc"
        val retString = Await.result(server1 ? Salute("nmd"), 10.seconds).asInstanceOf[String]
        println(retString)
    }

    override def main(args: Array[String]): Unit = {
        test()
//        system.terminate()
    }

}

