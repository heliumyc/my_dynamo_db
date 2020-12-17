package myutils

class Counter {

    private var counter: Map[Int, Int] = Map()

    def get(idx: Int): Int = {
        counter.getOrElse(idx, 0)
    }

    def countDown(idx: Int): Unit = {
        val v = get(idx) - 1
        counter = if(v <= 0) {
            counter - idx
        } else {
            counter + (idx -> v)
        }
    }

    def addCount(idx: Int, ): Unit = {

    }


}
