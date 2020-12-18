package environment

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.pattern.after

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.util.Random
import myutils.{SimpleLogger => Logger}

trait Fuzzed extends Actor {

    def send(target: ActorRef, msg: Any, delay: Double)(implicit context: ActorContext): Unit = {
        Logger.info(s"Send: ${context.self.path.name} send $msg to ${target.path.name}")
        // context.system.scheduler.scheduleOnce(1 second, target, msg)
        after(delay.milliseconds)(Future {
            target ! msg
        })(context.system)
    }

//    // this implicit usage is buggy, sender() is resolved at compile time
//    def reply(msg: Any)(implicit target: ActorRef, context: ActorContext): Unit = {
//        println(s"reply to ${target.path.name}")
//        send(target, msg)
//    }
}
