package environment

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.pattern.after

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble
import scala.util.Random

trait Fuzzed extends Actor {

    val randGen = new Random()

    val baseLatency = 2

    val latencyVariance = 10

    def getDelay: Double = baseLatency + latencyVariance * Math.abs(randGen.nextGaussian())

    def send(target: ActorRef, msg: Any)(implicit context: ActorContext): Unit = {
        println(s"${context.self.path.name} send $msg to ${target.path.name}")
        // context.system.scheduler.scheduleOnce(1 second, target, msg)
        after(getDelay seconds)(Future {
            target ! msg
        })(context.system)
    }

    def reply(msg: Any)(implicit target: ActorRef, context: ActorContext): Unit = {
        send(target, msg)
    }
}
