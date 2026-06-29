package ro.trenuri.app.ui.common

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class AppDate(val year: Int, val month: Int, val day: Int) {
    fun format(): String {
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "${p(day)}.${p(month)}.$year"
    }

    fun nextDay(): AppDate {
        val ld = LocalDate(year, month, day).plus(1, DateTimeUnit.DAY)
        return AppDate(ld.year, ld.monthNumber, ld.dayOfMonth)
    }
}

typealias Today = () -> AppDate
