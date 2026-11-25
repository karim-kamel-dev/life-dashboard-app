package dev.karim.lifedashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Representative synthetic dataset on a real emulator: screens in several languages, full export, backup restore equality. */
class FullTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val out get() = File(ctx.filesDir, "ci").also { it.mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        File(out, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun makePhoto(dir: File, name: String, color: Int) {
        val bmp = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val p = Paint(); p.shader = LinearGradient(0f, 0f, 480f, 480f, color, android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
        Canvas(bmp).drawRect(0f, 0f, 480f, 480f, p)
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun dataset(): AppData {
        val today = LocalDate.now()
        val days = LinkedHashMap<String, Day>()
        val notes = listOf("مشيت نص ساعة", "Short run, felt good", "تعبان شوية", "كتبت فكرة جديدة", "")
        for (i in 0 until 75) {
            if (i % 7 == 4 || i % 11 == 9) continue // unlogged days stay unlogged
            val ans = HashMap<Int, Int>()
            for (a in 0 until 8) {
                val v = (i * 3 + a * 5) % 7
                if (v < 3) ans[a] = DONE else if (v < 5) ans[a] = NOTDONE // others stay unanswered
            }
            val nt = HashMap<Int, String>(); if (notes[i % 5].isNotEmpty()) nt[i % 8] = notes[i % 5]
            val hrs = HashMap<Int, String>(); hrs[8 + i % 3] = "شغل"; hrs[13] = "Lunch"; if (i % 2 == 0) hrs[20] = "تمرين"
            val inc = if (i % 5 == 0) 150.0 + i else null
            val exp = if (i % 3 == 0) 40.5 + i else null
            days[today.minusDays(i.toLong()).toString()] = Day(ans, nt, inc, exp, hrs)
        }
        val colors = listOf(0xFF6B8E4E, 0xFFB0894E, 0xFF4E7FA8, 0xFFA84E6B, 0xFF8E4EA8, 0xFF4EA89A)
        val photos = colors.mapIndexed { i, _ -> Photo("p$i", "f$i.jpg", today.minusDays((i * 3).toLong()).toString(), i % 8, if (i % 2 == 0) "تجربة $i" else "test $i") }
        return AppData(days = days, photos = photos, opening = Opening(2000.0, today.minusDays(70).toString()))
    }

    @Test fun fullDatasetScreensExportAndRestore() {
        val real = Store(ctx.filesDir)
        val colors = listOf(0xFF6B8E4E, 0xFFB0894E, 0xFF4E7FA8, 0xFFA84E6B, 0xFF8E4EA8, 0xFF4EA89A)
        colors.forEachIndexed { i, c -> makePhoto(real.photosDir, "f$i.jpg", c.toInt()) }
        val base = dataset()
        val trace = StringBuilder()
        for (lang in listOf("ar", "en", "de", "zh", "ru")) {
            val data = base.copy(lang = lang)
            real.save(data)
            rule.activityRule.scenario.recreate(); rule.waitForIdle()
            for (t in 0..4) {
                rule.onAllNodesWithText(tr("tab$t")).onLast().performClick()
                Thread.sleep(900); shot("full_${lang}_tab$t")
            }
            trace.appendLine("$lang screens ok")
        }
        File(out, "full_trace.txt").writeText(trace.toString())

        // full export text + html in Arabic and English, whole range, photos included
        val today = LocalDate.now(); val from = today.minusDays(74)
        for (lang in listOf("ar", "en")) {
            Lang.cur = lang
            val text = buildExport(base, from, today, setOf("freq", "details", "money", "question"))
            File(out, "export_full_$lang.txt").writeText(text)
            val html = buildHtmlDoc(real, base, text, from, today, true)
            assertEquals(6, Regex("data:image/jpeg;base64,").findAll(html).count())
            File(out, "export_full_$lang.html").writeText(html)
            assertTrue(text.contains(tr("nodata")))
            val some = buildHtmlDoc(real, base, text, from, today, true, setOf("p0", "p2"))
            assertEquals(2, Regex("data:image/jpeg;base64,").findAll(some).count())
            val none = buildHtmlDoc(real, base, text, from, today, true, emptySet())
            assertEquals(0, Regex("data:image/jpeg;base64,").findAll(none).count())
        }

        // backup -> restore equality on the full dataset
        val zip = File(out, "full_backup.zip")
        zip.outputStream().use { Backup.write(real, base, it) }
        val s2 = Store(File(ctx.cacheDir, "restore" + System.nanoTime()).also { it.mkdirs() })
        val back = zip.inputStream().use { Backup.restore(s2, it) }
        assertEquals(base, back)
        assertEquals(base, Store(s2.dir).load())
        for (i in 0 until 6) assertEquals(File(real.photosDir, "f$i.jpg").length(), File(s2.photosDir, "f$i.jpg").length())
        File(out, "full_restore.txt").writeText("restore equal: days=${base.days.size} photos=${base.photos.size}\n")
        real.save(AppData())
    }

    @Test fun perPhotoSelectionInSettings() {
        val real = Store(ctx.filesDir)
        val colors = listOf(0xFF6B8E4E, 0xFFB0894E, 0xFF4E7FA8, 0xFFA84E6B, 0xFF8E4EA8, 0xFF4EA89A)
        colors.forEachIndexed { i, c -> makePhoto(real.photosDir, "f$i.jpg", c.toInt()) }
        for (lang in listOf("en", "ar")) {
            real.save(dataset().copy(lang = lang))
            rule.activityRule.scenario.recreate(); rule.waitForIdle()
            rule.onAllNodesWithText(tr("tab4")).onLast().performClick(); Thread.sleep(700)
            rule.onNodeWithText(tr("sec_photos")).performScrollTo().performClick(); Thread.sleep(700)
            rule.onNodeWithText(tr("pick_photos", 6, 6)).performScrollTo(); Thread.sleep(500)
            shot("sel_${lang}_all"); rule.onNodeWithText(tr("pick_none")).performScrollTo(); shot("sel_${lang}_all_thumbs")
            rule.onNodeWithText(tr("pick_none")).performScrollTo().performClick(); Thread.sleep(500)
            rule.onNodeWithText(tr("pick_photos", 0, 6)).assertExists()
            shot("sel_${lang}_none")
            rule.onNodeWithText(tr("pick_all")).performScrollTo().performClick(); Thread.sleep(500)
            rule.onNodeWithText(tr("pick_photos", 6, 6)).assertExists()
            // toggle single photos via the semantics click action (no coordinates), log the visible count after each step
            fun tog(id: String) { rule.onNodeWithTag("selphoto_$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); rule.waitForIdle(); Thread.sleep(400) }
            tog("p1"); shot("sel_${lang}_one"); tog("p4"); shot("sel_${lang}_four")
            val have = listOf(3, 4, 5, 6).map { n -> n to rule.onAllNodesWithText(tr("pick_photos", n, 6)).fetchSemanticsNodes().size }
            File(out, "sel_${lang}_counts.txt").writeText("after p1,p4 off: " + have.joinToString { "${it.first}of6=${it.second}" } + "\n")
            rule.onNodeWithText(tr("pick_photos", 4, 6)).assertExists()
            tog("p1")
            rule.onNodeWithText(tr("pick_photos", 5, 6)).assertExists()
            File(out, "sel_${lang}_result.txt").writeText("toggle ok: none->0, all->6, two off->4, one back->5\n")
        }
        real.save(AppData())
    }
}
