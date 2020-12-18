package myutils

object CollectionUtils {

    def removeElement[T](list: List[T], element: T): List[T] = {
        list.filterNot(_ == element)
    }

    def removeElement[T](list: List[T], filterFunc: T => Boolean): List[T] = {
        list.filterNot(filterFunc)
    }

    def combineMap[K,V](m1: Map[K,V], m2: Map[K,V], combineFunc: (V, V)=>V): Map[K,V] = {
        (m1.keySet ++ m2.keySet).map(k => k -> (m1.get(k)++m2.get(k)).reduceOption(combineFunc).get).toMap
    }

}
