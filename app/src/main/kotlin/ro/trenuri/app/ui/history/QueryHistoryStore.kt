package ro.trenuri.app.ui.history

interface QueryHistoryStore<T> {
    fun recent(): List<T>
    fun add(item: T)
}
