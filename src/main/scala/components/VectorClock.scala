package components

import Order._

case class VectorClock[Id](private val clock: Map[Id, Counter]) {
  def get(id: Id): Option[Counter] = clock.get(id)

  def set(id: Id, counter: Counter): VectorClock[Id] = VectorClock(clock + (id -> counter))

  def increase(id: Id): VectorClock[Id] = {
    val searchedCounter = clock.getOrElse(id, Counter.zero)
    val updatedCounter = searchedCounter.addOne()

    VectorClock(clock + (id -> updatedCounter))
  }
}

object VectorClock {
  def apply[Id](): VectorClock[Id] = {
    apply(Map[Id, Counter]())
  }

  def merge[Id](receiverId: Id, vc1: VectorClock[Id], vc2: VectorClock[Id]): VectorClock[Id] = {
    val mergedClocksSet = vc1.clock.keySet ++ vc2.clock.keySet map { i =>
      i -> Counter.max(vc1.clock.getOrElse(i, Counter.zero), vc2.clock.getOrElse(i, Counter.zero))
    }
    val mergedClocks = mergedClocksSet.toMap
    val counter = mergedClocks.getOrElse(receiverId, Counter.zero).addOne()

    VectorClock(mergedClocks + (receiverId -> counter))
  }

  def compare[Id](vc1: VectorClock[Id], vc2: VectorClock[Id]): Order = {
    val compareResult = vc1.clock.keySet ++ vc2.clock.keySet map { i =>
      i -> Counter.compare(vc1.clock.getOrElse(i, Counter.zero), vc2.clock.getOrElse(i, Counter.zero))
    }
    val allNotBefore = compareResult.forall { case (_, order) => order != before }
    val allNotAfter = compareResult.forall { case (_, order) => order != after }
    val anyBefore = compareResult.exists { case (_, order) => order == before }
    val anyAfter = compareResult.exists { case (_, order) => order == after }

    () match {
      case _ if allNotBefore && anyAfter => after
      case _ if allNotBefore => concurrent
      case _ if allNotAfter && anyBefore => before
      case _ => concurrent
    }
  }
}