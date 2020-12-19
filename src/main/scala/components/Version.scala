package components
import myutils.VectorClock

object Version {
    def apply(): Version = VectorClock[Host]()

    type Version = VectorClock[Host]
}
