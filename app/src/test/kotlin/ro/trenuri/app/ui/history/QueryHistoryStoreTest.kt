package ro.trenuri.app.ui.history

import kotlin.test.Test
import kotlin.test.assertEquals

class QueryHistoryStoreTest {
    @Test
    fun capsAndDedupsMostRecentFirst() {
        val s = InMemoryQueryHistoryStore<String>(cap = 3)
        s.add("a"); s.add("b"); s.add("a"); s.add("c"); s.add("d")
        // Trace:
        //   add("a") → ["a"]
        //   add("b") → ["b","a"]
        //   add("a") → remove "a" → ["b"], prepend → ["a","b"]
        //   add("c") → ["c","a","b"]  (size==cap, no trim)
        //   add("d") → prepend → ["d","c","a","b"], trim → ["d","c","a"]
        // "b" is evicted; "a" moved to front then pushed back by c/d
        assertEquals(listOf("d", "c", "a"), s.recent())
    }

    @Test
    fun emptyStoreReturnsEmptyList() {
        val s = InMemoryQueryHistoryStore<Int>(cap = 5)
        assertEquals(emptyList(), s.recent())
    }

    @Test
    fun capOfOneKeepsOnlyMostRecent() {
        val s = InMemoryQueryHistoryStore<String>(cap = 1)
        s.add("x"); s.add("y"); s.add("z")
        assertEquals(listOf("z"), s.recent())
    }

    @Test
    fun reAddingItemMovesItToFront() {
        val s = InMemoryQueryHistoryStore<String>(cap = 10)
        s.add("a"); s.add("b"); s.add("c")
        s.add("a")
        assertEquals(listOf("a", "c", "b"), s.recent())
    }
}
