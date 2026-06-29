package ro.trenuri.app.ui.common

data class AppDate(val year: Int, val month: Int, val day: Int) {
    fun format(): String {
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "${p(day)}.${p(month)}.$year"
    }
}

typealias Today = () -> AppDate
