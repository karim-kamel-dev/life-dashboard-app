package dev.karim.lifedashboard

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LangTest {
    private val ph = Regex("%[0-9][$]s")
    @Test fun everyLanguageHasEveryStringWithSamePlaceholders() {
        val ar = TABLE["ar"]!!
        for ((code, _) in Lang.list) {
            val t = TABLE[code]!!
            for ((k, v) in ar) {
                val x = t[k]; assertNotNull("$code missing $k", x)
                assertEquals("$code placeholders $k", ph.findAll(v).map { it.value }.sorted().toList(), ph.findAll(x!!).map { it.value }.sorted().toList())
            }
        }
    }
    @Test fun exportInEveryLanguageKeepsMissingAsNotRecordedAndNoZero() {
        val data = AppData(days = mapOf("2026-11-25" to Day(answers = mapOf(0 to DONE), expense = 10.0)), opening = Opening(100.0, "2026-11-01"))
        for ((code, _) in Lang.list) {
            Lang.cur = code
            val s = buildExport(data, LocalDate.parse("2026-11-24"), LocalDate.parse("2026-11-25"), setOf("freq", "details", "money", "question"))
            assertTrue("$code no format leak", !s.contains("%1") && !s.contains("%2"))
            assertTrue("$code shows not-recorded for income", s.contains(tr("nodata")))
        }
        Lang.cur = "ar"
    }
}
