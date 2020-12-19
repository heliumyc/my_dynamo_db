package components

class HintsStorage[Value] {

    type HintId = Int

    var hintId: HintId = 0
    var internalData: Map[HintId, Value] = Map()

    def add(value: Value): HintId = {
        hintId += 1
        internalData += hintId -> value
        hintId
    }

    def remove(hintId: HintId): Option[Value] = {
        val v = internalData.get(hintId)
        internalData -= hintId
        v
    }

}

object HintsStorage {
    def apply[T](): HintsStorage[T] = {
        new HintsStorage[T]()
    }
}