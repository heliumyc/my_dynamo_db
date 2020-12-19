package myutils

object Order extends Enumeration {
  type Order = Value
  val BEFORE, AFTER, CONCURRENT, SAME = Value
}
