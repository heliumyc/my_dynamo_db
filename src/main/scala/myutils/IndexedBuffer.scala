package myutils

class IndexedBuffer[Id, Element] {
    private var buffer: Map[Id, List[Element]] = Map()

    def get(id: Id): List[Element] = {
        buffer.getOrElse(id, List())
    }

    def add(id: Id, element: Element): IndexedBuffer[Id, Element] = {
        val elementList = element :: get(id)
        buffer += (id -> elementList)
        this
    }

    def add(id: Id, elements: List[Element]): IndexedBuffer[Id, Element] = {
        val elementList = elements ++ get(id)
        buffer += (id -> elementList)
        this
    }

    def set(id: Id, elements: List[Element]): IndexedBuffer[Id, Element] = {
        buffer += (id -> elements)
        this
    }

    def remove(id: Id, element: Element): IndexedBuffer[Id, Element] = {
        val elementList = CollectionUtils.removeElement(get(id), element)
        if (elementList.isEmpty) {
            buffer -= id
        } else {
            buffer += (id -> elementList)
        }
        this
    }

    def remove(id: Id): IndexedBuffer[Id, Element] = {
        buffer -= id
        this
    }

    def +(id: Id, elements: List[Element]): IndexedBuffer[Id, Element] = {
        set(id, elements)
    }
}

object IndexedBuffer {
    def apply[T,U](): IndexedBuffer[T,U] = {
        new IndexedBuffer[T,U]()
    }
}
