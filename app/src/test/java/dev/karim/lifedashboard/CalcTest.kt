package dev.karim.lifedashboard

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CalcTest {
    @Test fun parse() {
        assertEquals(1234.5 to true, parseAmount("١٢٣٤٫٥"))
        assertEquals(null to true, parseAmount("  "))
        assertEquals(0.0 to true, parseAmount("0"))
        assertFalse(parseAmount("abc").second)
        assertFalse(parseAmount("-5").second)
    }
    @Test fun hex() { assertEquals(0xFF4C9F70L, parseHexColor("#4c9f70")); assertNull(parseHexColor("zzz")); assertNull(parseHexColor("#12345")) }
    @Test fun stats() {
        val days = mapOf(
            "2026-11-01" to Day(answers = mapOf(0 to DONE, 1 to NOTDONE)),
            "2026-11-02" to Day(answers = mapOf(0 to DONE)),
            "2026-11-03" to Day(notes = mapOf(0 to "x")), // no answers: not a recorded day
        )
        val (s, rec) = activityStats(days, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))
        assertEquals(2, rec)
        assertEquals(2, s[0].done); assertEquals(100, s[0].pct)
        assertEquals(1, s[1].notDone); assertEquals(1, s[1].unanswered)
        assertEquals(2, s[2].unanswered); assertNull(s[2].pct)
    }
    @Test fun money() {
        val days = mapOf("2026-11-01" to Day(expense = 80.5), "2026-11-02" to Day(income = 0.0, expense = 0.0), "2026-11-03" to Day())
        val r = moneyRows(days, Opening(1000.0, "2026-11-01"))
        assertEquals(2, r.size)
        assertNull(r[0].income); assertEquals(919.5, r[0].balance!!, 1e-9)
        assertEquals(0.0, r[1].income!!, 0.0)
        assertNull(moneyRows(days, Opening())[0].balance) // no opening -> no balance
    }
    @Test fun exportWording() {
        val d = AppData(days = mapOf("2026-11-01" to Day(expense = 10.0)), opening = Opening(100.0, "2026-11-01"))
        val t = buildExport(d, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"), setOf("money"))
        assertTrue(t.contains(NO_DATA)); assertTrue(t.contains("الرصيد المحسوب 90"))
    }
    @Test fun roundTripPersistence() {
        val dir = createTempDir()
        val s = Store(dir)
        val d = AppData(days = mapOf("2026-11-01" to Day(mapOf(0 to DONE, 3 to NOTDONE), mapOf(0 to "غدا"), 5.0, null, mapOf(9 to "شغل"))),
            photos = listOf(Photo("1", "a.jpg", "2026-11-01", 0, "c")), opening = Opening(10.0, "2026-11-01"), palette = "mint", mode = "light", accent = 0xFF112233)
        s.save(d)
        assertEquals(d, Store(dir).load()) // fresh Store = like an app restart (JVM only, not a phone)
        // backup / restore
        File(s.photosDir, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val bo = java.io.ByteArrayOutputStream(); Backup.write(s, d, bo)
        val dir2 = createTempDir(); val s2 = Store(dir2)
        assertEquals(d, Backup.restore(s2, bo.toByteArray().inputStream()))
        assertArrayEquals(byteArrayOf(1, 2, 3), File(s2.photosDir, "a.jpg").readBytes())
        assertNull(Backup.restore(s2, "garbage".toByteArray().inputStream()))
    }
}
