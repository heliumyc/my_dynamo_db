package components

import components.Message.Value
import components.Version.Version

case class Record(value: Value, version: Version, timestamp: Long = System.currentTimeMillis()) {

    def updateVersion(version: Version): Record = {
        Record(this.value, version)
    }

    def getHash: Int = {
        value.hashCode
    }
}
