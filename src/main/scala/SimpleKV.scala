import akka.actor.{Actor, ActorSystem, Props}
import environment.ProxyActor
import kvstore.Server._

object SimpleKV {
  def run(): Unit = {
    val system = ActorSystem("KV")
    val server = system.actorOf(Props(new ProxyActor[kvstore.Server]), name = "server")

    val _ = system.actorOf(Props(new Actor {
      override def receive: Receive = {
        case Result(key, Some(value))=>
          println(s"The value of $key is $value")
          context.system.terminate()
        case _ =>
      }

      override def preStart: Unit = {
        server ! Put(12, 3)
        server ! Put(1, 5)
        server ! Put(12, 1)
        server ! Get(12)
      }
    }), name = "client")
  }
}
