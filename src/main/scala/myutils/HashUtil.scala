package myutils

object HashUtil {

    /**
     * FNV1_32_HASH algorithm to calculate hash code
     * @param str string to be hashed
     * @return hash code
     */
    def getHash(str: String): Int = {
        val p = 16777619
        var hash = str.foldLeft(2166136261L.toInt)((hash, c) => (hash ^ c) * p)
        hash += hash << 13
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;
        hash = Math.abs(hash)
        hash
    }

}
