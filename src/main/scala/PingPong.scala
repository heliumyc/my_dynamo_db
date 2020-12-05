import akka.actor.{ActorRef, ActorSystem, Props}
import environment.{EmulatedActor, Fuzzed, Logged}

object PingPong {

    case object PingMessage

    case object PongMessage

    case object StartMessage

    case object StopMessage

    class Ping(pong: ActorRef) extends EmulatedActor with Logged {
        var count = 0

        def incrementAndPrint {
            count += 1; println("ping " + count)
        }

        override def receiveMsg: Receive = {
            case StartMessage =>
                incrementAndPrint
                pong ! PingMessage
            case PongMessage =>
                if (count > 9) {
                    sender ! StopMessage
                    println("ping stopped")
                    context.stop(self)
                } else {
                    incrementAndPrint
                    sender ! PingMessage
                }
        }
    }

    class Pong extends EmulatedActor with Logged {
        override def receiveMsg: Receive = {
            case PingMessage =>
                println("  pong")
                sender ! PongMessage
            case StopMessage =>
                println("pong stopped")
                context.stop(self)
                context.system.terminate()
        }

    }

    def run(): Unit = {
        val system = ActorSystem("PingPongSystem")
        val pong = system.actorOf(Props[Pong], name = "pong")
        val ping = system.actorOf(Props(new Ping(pong)), name = "ping")
        // start them going
        ping ! StartMessage
    }

}
