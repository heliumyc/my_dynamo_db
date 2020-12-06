package kvstore

import kvstore.Order._


case class Counter(value: Int) extends AnyVal {
  def addOne(): Counter = this.copy(value = value + 1)
}

private object Counter {
  def max(c1: Counter, c2: Counter): Counter = {
    Counter(scala.math.max(c1.value, c2.value))
  }

  def compare(c1: Counter, c2: Counter): Order = {
    if (c1.value < c2.value) before
    else if (c1.value > c2.value) after
    else concurrent
  }

  def zero: Counter = Counter(0)
}