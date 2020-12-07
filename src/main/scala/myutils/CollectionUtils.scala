package myutils

object CollectionUtils {

    def removeElement[T](list: List[T], element: T): List[T] = {
        list.filterNot(_ == element)
    }

}
