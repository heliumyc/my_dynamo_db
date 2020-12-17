package environment

import scala.reflect.ClassTag

import akka.actor.{Actor, Props}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}

@Deprecated
class ProxyActor[A <: EmulatedActor: ClassTag] extends Actor {
  var router: Router = {
    val routees = Vector.fill(1) {
      val r = context.actorOf(Props[A](), name = "_" + self.path.name)
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  override def receive: Receive = {
    case message =>
      Thread.sleep(2000)
      router.route(message, sender())
  }
}

