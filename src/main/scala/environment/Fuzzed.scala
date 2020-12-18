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

    val delayFactor = 10

    val scaleFactorSqrt: Double = Math.sqrt(delayFactor)

    /**
     * according to simple statistics
     * 68.3% is in [baseLatency, baseLatency + scaleFactor]
     * 95.4% is in [baseLatency, baseLatency + 2*scaleFactor]
     * 99.7% is in [baseLatency, baseLatency + 3*scaleFactor]
     */
    def getDelay: Double = baseLatency + scaleFactorSqrt * Math.abs(randGen.nextGaussian())

    def send(target: ActorRef, msg: Any)(implicit context: ActorContext): Unit = {
        println(s"Send: ${context.self.path.name} send $msg to ${target.path.name}")
        // context.system.scheduler.scheduleOnce(1 second, target, msg)
        after(getDelay seconds)(Future {
            target ! msg
        })(context.system)
    }

    def reply(msg: Any)(implicit target: ActorRef, context: ActorContext): Unit = {
        send(target, msg)
    }
}
