import akka.actor.{Actor, ActorSystem, Props}
import environment.{EmulatedActor, Fuzzed, Logged}

object SimpleCounter {

    case class Inc(x: Int)

    case class Dec(y: Int)

    case class Get()

    case class Reset()

    case class Message(s: String) {
        override def toString: String = s
    }

    class Server extends EmulatedActor with Logged {

        var count:Int = 0

        override protected def receiveMsg: Receive = {
            case Inc(x) =>
                count += x
            case Dec(y) =>
                count -= y
            case Get() =>
                sender() ! Message(count.toString)
            case Reset() =>
                count = 0
        }
    }

    def run(): Unit = {
        val system = ActorSystem("Counter")
        val server = system.actorOf(Props[Server], name = "server")

        val client = system.actorOf(Props(new Actor {
            override def receive: Receive = {
                case Message(x) =>
                    assert(x.toInt == 0)
                    println(x)
                    context.system.terminate()
            }
            override def preStart: Unit = {
                server ! Reset()
                server ! Inc(1)
                server ! Inc(1)
                server ! Inc(1)
                server ! Dec(3)
                server ! Get()
            }
        }), name = "client")

    }

}
