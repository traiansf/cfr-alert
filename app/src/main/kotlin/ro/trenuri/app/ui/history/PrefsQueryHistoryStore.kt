package ro.trenuri.app.ui.history

import android.content.SharedPreferences

/**
 * A [QueryHistoryStore] that persists the recent list to [SharedPreferences].
 *
 * Each record is one line in the stored string (newline-joined).
 * Within a record, fields are delimited by [FS] (ASCII Unit Separator U+001F),
 * which cannot appear in normal user-visible text.
 *
 * The list is stored most-recent-first; deserialization restores that order.
 * All cap/dedup logic is delegated to [InMemoryQueryHistoryStore].
 */
class PrefsQueryHistoryStore<T>(
    private val prefs: SharedPreferences,
    private val key: String,
    cap: Int,
    private val serialize: (T) -> String,
    private val deserialize: (String) -> T?,
) : QueryHistoryStore<T> {

    private val delegate = InMemoryQueryHistoryStore<T>(cap)

    init {
        val raw = prefs.getString(key, "") ?: ""
        if (raw.isNotBlank()) {
            // Stored order is most-recent-first; add oldest-first so delegate ends up correct.
            raw.split("\n")
                .filter { it.isNotEmpty() }
                .mapNotNull { runCatching { deserialize(it) }.getOrNull() }
                .reversed()
                .forEach { delegate.add(it) }
        }
    }

    override fun recent(): List<T> = delegate.recent()

    override fun add(item: T) {
        delegate.add(item)
        save()
    }

    private fun save() {
        val raw = delegate.recent().joinToString("\n") { serialize(it) }
        prefs.edit().putString(key, raw).apply()
    }

    companion object {
        /** ASCII Unit Separator — safe field delimiter inside a serialized record. */
        const val FS = ""
    }
}
