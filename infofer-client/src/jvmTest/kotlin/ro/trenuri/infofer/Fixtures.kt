package ro.trenuri.infofer

object Fixtures {
    fun load(name: String): String {
        val stream = this::class.java.classLoader.getResourceAsStream("fixtures/$name")
            ?: error("fixture not found: fixtures/$name")
        return stream.use { it.readBytes().decodeToString() }
    }
}
