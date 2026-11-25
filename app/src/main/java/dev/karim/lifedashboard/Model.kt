package dev.karim.lifedashboard

import java.time.LocalDate

val ACTIVITIES: List<String> get() = List(8) { tr("act$it") }
const val DONE = 1
const val NOTDONE = 2

data class Day(
    val answers: Map<Int, Int> = emptyMap(),
    val notes: Map<Int, String> = emptyMap(),
    val income: Double? = null,
    val expense: Double? = null,
    val hours: Map<Int, String> = emptyMap(),
) {
    fun isEmpty() = answers.isEmpty() && notes.values.all { it.isBlank() } && income == null && expense == null && hours.values.all { it.isBlank() }
}

data class Photo(val id: String, val file: String, val date: String, val activity: Int, val caption: String = "")
data class Opening(val amount: Double? = null, val date: String? = null)
data class AppData(
    val days: Map<String, Day> = emptyMap(),
    val photos: List<Photo> = emptyList(),
    val opening: Opening = Opening(),
    val palette: String = "olive",
    val mode: String = "dark", // dark, light, system
    val accent: Long? = null,
    val lang: String = "auto",
)

data class ActStat(val done: Int, val notDone: Int, val unanswered: Int) {
    val answered get() = done + notDone
    val pct: Int? get() = if (answered == 0) null else Math.round(done * 100f / answered)
}

data class MoneyRow(val date: String, val income: Double?, val expense: Double?, val change: Double, val balance: Double?)

fun dayCount(d: Day?): Pair<Int, Int> = if (d == null) 0 to 0 else d.answers.values.count { it == DONE } to d.answers.values.count { it == NOTDONE }

/** Days in the range that have at least one answer are "recorded". Unanswered activities on recorded days are counted separately, never as "no". */
fun activityStats(days: Map<String, Day>, from: LocalDate, to: LocalDate): Pair<List<ActStat>, Int> {
    var recorded = 0
    val done = IntArray(ACTIVITIES.size); val no = IntArray(ACTIVITIES.size); val un = IntArray(ACTIVITIES.size)
    var d = from
    while (!d.isAfter(to)) {
        val day = days[d.toString()]
        if (day != null && day.answers.isNotEmpty()) {
            recorded++
            for (i in ACTIVITIES.indices) when (day.answers[i]) { DONE -> done[i]++; NOTDONE -> no[i]++; else -> un[i]++ }
        }
        d = d.plusDays(1)
    }
    return ACTIVITIES.indices.map { ActStat(done[it], no[it], un[it]) } to recorded
}

/** Returns (value, valid). Empty text = no data (null, valid). Accepts Arabic digits and Arabic decimal separators. */
fun parseAmount(s: String): Pair<Double?, Boolean> {
    val t = s.trim()
    if (t.isEmpty()) return null to true
    val sb = StringBuilder()
    for (c in t) sb.append(when (c) {
        in '٠'..'٩' -> '0' + (c - '٠')
        in '۰'..'۹' -> '0' + (c - '۰')
        '٫', '،', ',' -> '.'
        else -> c
    })
    val v = sb.toString().toDoubleOrNull()
    return if (v == null || v.isNaN() || v.isInfinite() || v < 0) null to false else v to true
}

/** Rows only for days with at least one money field entered. A missing field stays null (no data), it is not zero.
 *  Balance exists only if an opening amount was set; it is computed from entries, not the real balance. */
fun moneyRows(days: Map<String, Day>, opening: Opening, from: String? = null): List<MoneyRow> {
    val start = opening.date
    var bal = opening.amount
    val out = ArrayList<MoneyRow>()
    for (k in days.keys.sorted()) {
        if (start != null && k < start) continue
        val d = days[k]!!
        if (d.income == null && d.expense == null) continue
        val ch = (d.income ?: 0.0) - (d.expense ?: 0.0)
        bal = bal?.plus(ch)
        if (from == null || k >= from) out.add(MoneyRow(k, d.income, d.expense, ch, bal))
    }
    return out
}

fun fmt(v: Double): String = if (v == Math.rint(v) && Math.abs(v) < 1e12) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

val NO_DATA: String get() = tr("nodata")

fun buildExport(data: AppData, from: LocalDate, to: LocalDate, sections: Set<String>): String {
    val sb = StringBuilder()
    sb.appendLine(tr("ex_title", from, to))
    sb.appendLine(tr("ex_note"))
    if ("freq" in sections) {
        val (st, rec) = activityStats(data.days, from, to)
        sb.appendLine().appendLine(tr("ex_freq"))
        sb.appendLine(tr("ex_days_rec", rec))
        ACTIVITIES.forEachIndexed { i, a ->
            val s = st[i]
            sb.appendLine(tr("ex_stat", a, s.done, s.notDone, s.unanswered) + (s.pct?.let { tr("ex_pct", it) } ?: ""))
        }
    }
    if ("details" in sections) {
        sb.appendLine().appendLine(tr("ex_details"))
        var d = from
        while (!d.isAfter(to)) {
            val day = data.days[d.toString()]
            if (day != null && (day.answers.isNotEmpty() || day.notes.values.any { it.isNotBlank() } || day.hours.values.any { it.isNotBlank() })) {
                sb.appendLine(d.toString())
                ACTIVITIES.forEachIndexed { i, a ->
                    val ans = when (day.answers[i]) { DONE -> tr("done"); NOTDONE -> tr("notdone"); else -> null }
                    val note = day.notes[i]?.takeIf { it.isNotBlank() }
                    val ph = data.photos.count { it.date == d.toString() && it.activity == i }
                    if (ans != null || note != null || ph > 0) sb.appendLine("  - $a: ${ans ?: tr("unans")}" + (note?.let { " | $it" } ?: "") + (if (ph > 0) tr("ex_photos", ph) else ""))
                }
                day.hours.toSortedMap().forEach { (h, t) -> if (t.isNotBlank()) sb.appendLine(tr("ex_hour", h, t)) }
            }
            d = d.plusDays(1)
        }
    }
    if ("money" in sections) {
        sb.appendLine().appendLine(tr("ex_money"))
        val op = data.opening
        sb.appendLine(tr("ex_open", op.amount?.let { fmt(it) + (op.date?.let { d -> tr("ex_on", d) } ?: "") } ?: NO_DATA))
        val rows = moneyRows(data.days, op).filter { it.date >= from.toString() && it.date <= to.toString() }
        if (rows.isEmpty()) sb.appendLine(tr("ex_nomoney"))
        rows.forEach { r ->
            sb.appendLine(tr("ex_row", r.date, r.income?.let { fmt(it) } ?: NO_DATA, r.expense?.let { fmt(it) } ?: NO_DATA, fmt(r.change), r.balance?.let { fmt(it) } ?: NO_DATA))
        }
        sb.appendLine(tr("ex_baldisc"))
    }
    if ("question" in sections) {
        sb.appendLine().appendLine(tr("ex_qtitle"))
        sb.appendLine(tr("ex_q"))
    }
    return sb.toString()
}
