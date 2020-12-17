import junit.framework.Assert.{assertEquals, assertFalse, assertTrue}
import junit.framework.TestCase
import myutils.Order._
import myutils.VectorClock

class VectorClockTest extends TestCase {

    def testLessOp(): Unit = {
        assertTrue(VectorClock() < VectorClock())
        assertTrue(VectorClock("a"->1, "b"->1) < VectorClock("a"->1, "b"->1))
        assertTrue(VectorClock("a"->1, "b"->1) < VectorClock("a"->1, "b"->2))
        assertTrue(VectorClock("b"->1) < VectorClock("a"->1, "b"->2))
        assertFalse(VectorClock("a"->1, "b"->2) < VectorClock("a"->1, "b"->1))
        assertFalse(VectorClock("a"->1, "c"->1) < VectorClock("a"->1, "b"->1))
    }

    def testGreaterOp(): Unit = {
        assertTrue(VectorClock() > VectorClock())
        assertTrue(VectorClock("a"->1, "b"->1) > VectorClock("a"->1, "b"->1))
        assertTrue(VectorClock("a"->1, "b"->2) > VectorClock("a"->1, "b"->1))
        assertTrue(VectorClock("a"->1, "b"->1, "c"->3) > VectorClock("a"->1, "b"->1))
        assertFalse(VectorClock("a"->1, "b"->1) > VectorClock("a"->1, "b"->1, "c"->3))
    }

    def testBefore(): Unit = {
        assertEquals(BEFORE, VectorClock("a"->1, "b"->1) compare VectorClock("a"->1, "b"->1))
        assertEquals(BEFORE, VectorClock("a"->1, "b"->1) compare VectorClock("a"->1, "b"->2))
        assertEquals(BEFORE, VectorClock("b"->1) compare VectorClock("a"->1, "b"->1))
    }

    def testAfter(): Unit = {
        // BEFORE is taken as default result for two same vector
//        assertEquals(AFTER, VectorClock("a"->1, "b"->1) compare VectorClock("a"->1, "b"->1))
        assertEquals(AFTER, VectorClock("a"->1, "b"->2) compare VectorClock("a"->1, "b"->1))
        assertEquals(AFTER, VectorClock("b"->1, "a"->1) compare VectorClock("b"->1))
    }

    def testConcurrent(): Unit = {
        assertEquals(CONCURRENT, VectorClock("a"->1, "b"->2) compare VectorClock("a"->2, "b"->1))
        assertEquals(CONCURRENT, VectorClock("a"->1, "b"->2) compare VectorClock("a"->1, "b"->1, "c"->2))
    }
}
