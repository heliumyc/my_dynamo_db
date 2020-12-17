package components

import components.Version.Version

case class Record(value: Set[String], version: Version, timestamp: Long = System.currentTimeMillis()) {

    def getHash: Int = {
        value.hashCode
    }
}
