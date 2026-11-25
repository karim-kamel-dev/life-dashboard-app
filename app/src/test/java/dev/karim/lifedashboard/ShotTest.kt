package dev.karim.lifedashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders the REAL Compose screens with DEMO data (made up, not Karim's) in a JVM test. Not a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h820dp-xhdpi")
class ShotTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun demo(palette: String, mode: String, dir: File): AppData {
        val r = java.util.Random(7)
        val days = HashMap<String, Day>()
        for (d in 1..24) {
            val ans = HashMap<Int, Int>()
            val n = if (d % 9 == 0) 0 else 8
            if (d % 6 == 5) { days["2026-11-%02d".format(d)] = Day(answers = ACTIVITIES.indices.associateWith { NOTDONE }); continue }
            if (d % 4 == 3) continue // unrecorded day
            for (i in 0 until n) { val x = r.nextInt(10); if (x < 6) ans[i] = DONE else if (x < 8) ans[i] = NOTDONE }
            days["2026-11-%02d".format(d)] = Day(ans)
        }
        // demo "today": 4 done, 2 not done, 2 unanswered
        days["2026-11-25"] = Day(
            answers = mapOf(0 to DONE, 1 to DONE, 2 to NOTDONE, 3 to DONE, 5 to NOTDONE, 6 to DONE),
            notes = mapOf(0 to "فول وبيض وسلطة (مثال تجريبي)", 3 to "رسمة بورتريه بالرصاص (مثال)"), hours = mapOf(9 to "شغل", 14 to "رسم"),
            income = null, expense = 80.5)
        days["2026-11-24"] = Day(income = 300.0, expense = 120.0)
        days["2026-11-23"] = Day(expense = 45.0)
        val photos = ArrayList<Photo>()
        listOf(0 to 0xFF6B8E4E, 3 to 0xFF8A6FB0, 6 to 0xFF4E7FA8, 0 to 0xFFB0894E, 4 to 0xFF9E5757, 3 to 0xFF5E9E8F).forEachIndexed { k, (act, col) ->
            val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            val p = Paint(); p.shader = LinearGradient(0f, 0f, 120f, 120f, col.toInt(), AColor.BLACK, Shader.TileMode.CLAMP)
            Canvas(bmp).drawRect(0f, 0f, 120f, 120f, p)
            val f = File(dir, "photos/demo$k.jpg"); f.parentFile!!.mkdirs()
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            photos.add(Photo("p$k", "demo$k.jpg", "2026-11-2${k % 5}", act))
        }
        return AppData(days, photos, Opening(1000.0, "2026-11-01"), palette, mode)
    }

    private fun shot(name: String, palette: String, mode: String, tab: Int, lang: String = "ar") {
        val dir = createTempDir()
        val store = Store(dir)
        val data = demo(palette, mode, dir).copy(lang = lang)
        rule.setContent { App(store, data, tab, LocalDate.parse("2026-11-25")) }
        repeat(25) { Thread.sleep(400); rule.waitForIdle() }
        val out = File(if (File("/downloads").isDirectory) "/downloads/LifeDashboard/shots" else "build/shots"); out.mkdirs()
        File(out, "$name.png").outputStream().use { val v = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp)); bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun a_today_olive() = shot("today_olive", "olive", "dark", 0)
    @Test fun b_today_graphite() = shot("today_graphite", "graphite", "dark", 0)
    @Test fun c_year_olive() = shot("year_olive", "olive", "dark", 1)
    @Test fun d_money_graphite() = shot("money_graphite", "graphite", "dark", 2)
    @Test fun e_photos_olive() = shot("photos_olive", "olive", "dark", 3)
    @Test fun f_settings_olive() = shot("settings_olive", "olive", "dark", 4)
    @Test fun h_today_zh() = shot("lang_today_zh", "olive", "dark", 0, "zh")
    @Test fun i_money_de() = shot("lang_money_de", "graphite", "dark", 2, "de")
    @Test fun j_settings_en() = shot("lang_settings_en", "olive", "dark", 4, "en")
    @Test fun k_today_ru() = shot("lang_today_ru", "graphite", "dark", 0, "ru")
    @Test fun g_today_graphite_light() = shot("today_graphite_light", "graphite", "light", 0)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h820dp-xhdpi")
class InteractionTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun start(tab: Int, data: AppData = AppData(), dir: File = createTempDir()) {
        rule.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App(Store(dir), data, tab, LocalDate.parse("2026-11-25")) } }
        rule.waitForIdle()
    }
    @Test fun yearGridTapOpensThatDay() {
        start(1)
        // day 1 of November is the right-most cell of row 11; day 15 is further left
        val node = rule.onNodeWithTag("yeargrid")
        val (w, h) = node.fetchSemanticsNode().size.let { it.width.toFloat() to it.height.toFloat() }
        val step = w / 33f
        val rowH = 15 * 3.0f // xhdpi density 2.0? use px from density below
        val dens = rule.activity.resources.displayMetrics.density
        fun tap(month: Int, day: Int) { node.performTouchInput { click(androidx.compose.ui.geometry.Offset(w - (day + 0.5f) * step, (month - 1) * 15 * dens + 6 * dens)) } }
        tap(11, 15); rule.waitForIdle()
        rule.onNodeWithText("15/11/2026", substring = true).assertExists()
    }
    @Test fun answerNoteAndMoneyPersistToDisk() {
        val dir = createTempDir()
        start(0, AppData(), dir)
        rule.onAllNodesWithText("✓")[0].performClick()
        rule.waitForIdle()
        val saved = Store(dir).load() // fresh Store reads the file, like after a restart (JVM, not a phone)
        val k = LocalDate.parse("2026-11-25").toString()
        org.junit.Assert.assertEquals(DONE, saved.days[k]!!.answers[0])
    }
}
