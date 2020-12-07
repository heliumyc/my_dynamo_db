package components

object Order extends Enumeration {
  type Order = Value
  val before, after, concurrent = Value
}
