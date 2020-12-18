package components

object ConflictResolvePolicies {

    def lastWriteWins(r1: Record, r2: Record): Record = {
        if (r1.timestamp > r2.timestamp) {
            r1
        } else {
            r2
        }
    }

    def mergeTwoSet(r1: Record, r2: Record): Record = {
        Record(r1.value ++ r2.value, r1.version.merge(r2.version))
    }

}
