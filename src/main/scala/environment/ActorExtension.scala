package environment

import akka.actor.Actor

@Deprecated
trait ActorExtension extends Actor {

    def receiveExtension: Receive = PartialFunction.empty

}
