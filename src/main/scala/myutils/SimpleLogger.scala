package myutils

object SimpleLogger {
    private val on = false
//    private val on = true

    def info(msg: String): Unit = {
        if (on) {
            println(msg)
        }
    }

}