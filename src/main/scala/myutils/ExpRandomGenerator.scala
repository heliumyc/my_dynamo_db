package myutils

import scala.util.Random

object ExpRandomGenerator extends Random {

    def nextExp(lambda: Double): Double = {
        Math.log(1 - this.nextDouble()) / (-lambda)
    }
}
