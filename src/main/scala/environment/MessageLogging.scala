package environment

import akka.actor.Actor
import components.Message

trait MessageLogging extends Actor {
    override def aroundReceive(receive: Receive, msg: Any): Unit = {
        msg match {
            case m: Message =>
                println(s"Recv: From ${sender().path.name} to ${this.self.path.name}: $m")
            case _ =>
        }
        super.aroundReceive(receive, msg)
    }
}
