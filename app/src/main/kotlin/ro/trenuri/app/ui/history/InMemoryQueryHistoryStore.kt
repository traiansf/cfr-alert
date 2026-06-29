package ro.trenuri.app.ui.history

class InMemoryQueryHistoryStore<T>(val cap: Int = 10) : QueryHistoryStore<T> {
    private val items = mutableListOf<T>()

    override fun recent(): List<T> = items.toList()

    override fun add(item: T) {
        items.remove(item)           // de-dup: remove existing occurrence (no-op if absent)
        items.add(0, item)           // prepend (most-recent-first)
        while (items.size > cap) items.removeAt(items.size - 1)  // enforce cap
    }
}
