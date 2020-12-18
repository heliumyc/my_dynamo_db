package myutils

import myutils.Order.{AFTER, BEFORE, CONCURRENT, SAME, Order}

case class VectorClock[Id](private val clock: Map[Id, Int] = Map()) {
    def get(id: Id): Int = clock.getOrElse(id, 0)

    def set(id: Id, counter: Int): VectorClock[Id] = VectorClock(clock + (id -> counter))

    def increase(id: Id): VectorClock[Id] = {
        val updatedCounter = get(id) + 1
        VectorClock(clock + (id -> updatedCounter))
    }

    def merge(other: VectorClock[Id]): VectorClock[Id] = {
        VectorClock(CollectionUtils.combineMap(this.clock, other.clock, Math.max))
    }

    /**
     * compare two vector clock
     * if two are the same, the return BEFORE (we cannot return two value)
     * @param other
     * @return
     */
    def compare(other: VectorClock[Id]): Order = {
        val isBefore = this < other
        val isAfter = other < this
        if (isBefore && isAfter) {
            SAME
        } else if (isBefore) {
            BEFORE
        } else if (isAfter) {
            AFTER
        } else {
            CONCURRENT
        }
    }

    def < (other: VectorClock[Id]): Boolean = {
        this.clock.map{case (k,v) => v <= other.get(k)}.reduce(_ && _)
    }

    def > (other: VectorClock[Id]): Boolean = {
        other < this
    }
}

object VectorClock {
    def apply[Id](elems: (Id, Int)*): VectorClock[Id] = {
        VectorClock[Id](elems.toMap)
    }
}
