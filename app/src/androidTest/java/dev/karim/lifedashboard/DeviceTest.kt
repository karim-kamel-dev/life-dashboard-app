package dev.karim.lifedashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/** Runs on a real Android emulator in CI. Uses a throwaway data dir, never anything real. */
class DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val out get() = File(ctx.filesDir, "ci").also { it.mkdirs() }

    private fun tmpStore(): Store = Store(File(ctx.cacheDir, "t" + System.nanoTime()).also { it.mkdirs() })

    private fun shot(name: String) {
        rule.waitForIdle()
        File(out, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun makePhoto(store: Store, name: String, color: Int) {
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val p = Paint(); p.shader = LinearGradient(0f, 0f, 400f, 400f, color, android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
        Canvas(bmp).drawRect(0f, 0f, 400f, 400f, p)
        File(store.photosDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    @Test fun realDeviceDataPhotosExportBackup() {
        val store = tmpStore()
        val day = "2026-10-01"
        listOf("a.jpg" to 0xFF6B8E4E.toInt(), "b.jpg" to 0xFFB0894E.toInt(), "c.jpg" to 0xFF4E7FA8.toInt()).forEach { makePhoto(store, it.first, it.second) }
        val data = AppData(
            days = mapOf(day to Day(mapOf(0 to DONE, 2 to NOTDONE), mapOf(0 to "اختبار جهاز"), null, 80.5, mapOf(9 to "شغل"))),
            photos = listOf(Photo("1", "a.jpg", day, 0, "تجريبي"), Photo("2", "b.jpg", day, 3), Photo("3", "c.jpg", day, 6)),
            opening = Opening(1000.0, "2026-11-01"))
        store.save(data)
        assertEquals(data, Store(store.dir).load())

        val real = Store(ctx.filesDir)
        store.photosDir.listFiles()!!.forEach { it.copyTo(File(real.photosDir, it.name), true) }
        real.save(data.copy(lang = "en"))
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText(tr("tab3")).performClick()
        Thread.sleep(3000); shot("device_photos")
        real.save(AppData())

        // HTML export with photos really embeds them
        val html = buildHtmlDoc(store, data, "x", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), true)
        assertEquals(3, Regex("data:image/jpeg;base64,").findAll(html).count())
        File(out, "export.html").writeText(html)
        val noPhotos = buildHtmlDoc(store, data, "x", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), false)
        assertFalse(noPhotos.contains("data:image"))

        // backup -> restore into a second empty store on the device
        val zip = File(out, "backup.zip")
        zip.outputStream().use { Backup.write(store, data, it) }
        val s2 = tmpStore()
        val back = zip.inputStream().use { Backup.restore(s2, it) }
        assertEquals(data, back)
        assertEquals(File(store.photosDir, "a.jpg").length(), File(s2.photosDir, "a.jpg").length())
        assertEquals(data, Store(s2.dir).load())
    }

    @Test fun tapSavesAndSurvivesNewStore() {
        rule.waitForIdle()
        shot("device_today_before")
        rule.onAllNodesWithText("✓")[0].performClick()
        rule.waitForIdle()
        shot("device_today_after")
        val saved = Store(ctx.filesDir).load()
        assertEquals(DONE, saved.days[LocalDate.now().toString()]!!.answers[0])
    }
}
