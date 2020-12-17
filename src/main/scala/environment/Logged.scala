package environment

import akka.actor.Actor

@Deprecated
trait Logged extends EmulatedActor with ActorExtension {

    abstract override def receiveExtension: Receive = {
        case msg =>
            println(s"From ${sender().path.name} to ${this.self.path.name}: $msg")
            super.receiveExtension.applyOrElse(msg, receiveMsg)
    }

}
