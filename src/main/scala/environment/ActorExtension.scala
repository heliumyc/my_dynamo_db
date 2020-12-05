package environment

import akka.actor.Actor

trait ActorExtension extends Actor {

    def receiveExtension: Receive = PartialFunction.empty

}
