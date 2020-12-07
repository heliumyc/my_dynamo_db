package components

case class Record(value: String, timestamp: Long) {

    def getHash: Int = {
        value.hashCode
    }
}
