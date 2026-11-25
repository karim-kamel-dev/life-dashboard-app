package dev.karim.lifedashboard

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.junit.Rule

/** Drives the REAL system document picker on the emulator (UiAutomator) to add a photo. Writes a trace to files/ci/picker_trace.txt. */
class PickerTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext

    @Test fun addPhotoThroughSystemPicker() {
        val ci = File(ctx.filesDir, "ci").also { it.mkdirs() }
        val trace = StringBuilder()
        fun t(s: String) { trace.appendLine(s); File(ci, "picker_trace.txt").writeText(trace.toString()) }
        // 1. put a real image into the device's shared media store
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(0f, 0f, 300f, 300f, Paint().apply { color = android.graphics.Color.rgb(200, 90, 40) })
        val cv = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "ld_picker_test.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png") }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        t("inserted image $uri")
        val dev = UiDevice.getInstance(inst)
        val before = Store(ctx.filesDir).load().photos.size
        // 2. tap "+ Photo" on the first activity
        rule.waitForIdle()
        rule.onAllNodesWithText(tr("add_photo"))[0].performClick()
        val shown = dev.wait(Until.hasObject(By.pkg("com.android.documentsui")), 15000)
        t("documentsui shown: $shown")
        dev.takeScreenshot(File(ci, "picker_1_open.png"))
        // 3. pick the image: try the visible file name first, then the Images root
        var picked = false
        runCatching { dev.findObject(By.text("ld_picker_test.png"))?.let { it.click(); picked = true; t("clicked file by name") } }.onFailure { t("name click failed: ${it.javaClass.simpleName}") }
        if (!picked) {
            runCatching {
                dev.findObject(By.desc("Show roots"))?.click(); dev.waitForIdle()
                dev.findObject(By.text("Images"))?.click(); dev.waitForIdle()
                dev.takeScreenshot(File(ci, "picker_2_images.png"))
                dev.findObject(By.text("ld_picker_test.png"))?.let { it.click(); picked = true; t("clicked file in Images root") }
            }.onFailure { t("roots path failed: ${it.javaClass.simpleName}") }
        }
        if (!picked) {
            // Hidden roots-drawer rows overlap the tile in the hierarchy, so coordinate taps hit them. Try coordinate-free routes first;
            // an attempt counts only when the picker has closed (our package is back in front).
            fun closed(): Boolean { dev.waitForIdle(); Thread.sleep(1200); return dev.wait(Until.hasObject(By.pkg(ctx.packageName)), 3000) && !dev.hasObject(By.pkg("com.android.documentsui")) }
            File(ci, "picker_hierarchy.xml").outputStream().use { dev.dumpWindowHierarchy(it) }
            // A: accessibility click on the tile or its nearest clickable ancestor
            runCatching {
                val root = inst.uiAutomation.rootInActiveWindow
                val nodes = root?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/icon_thumb").orEmpty()
                t("A: a11y icon_thumb nodes=${nodes.size}")
                var n = nodes.firstOrNull(); var depth = 0
                while (n != null && !picked && depth < 6) {
                    t("A: depth=$depth class=${n.className} clickable=${n.isClickable} id=${n.viewIdResourceName}")
                    if (n.isClickable && n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) { t("A: ACTION_CLICK performed at depth $depth"); if (closed()) picked = true else t("A: picker still open") }
                    n = n.parent; depth++
                }
            }.onFailure { t("A failed: ${it.javaClass.simpleName} ${it.message}") }
            // B: keyboard selection of the first item
            if (!picked && dev.hasObject(By.pkg("com.android.documentsui"))) runCatching {
                dev.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_DOWN); dev.waitForIdle()
                dev.pressKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER); t("B: dpad down + center")
                if (closed()) picked = true else t("B: picker still open")
            }.onFailure { t("B failed: ${it.javaClass.simpleName}") }
            // C: taps on the tile in the band between the hidden drawer rows (y 249..272 in the dump), then the tile's right part
            if (!picked && dev.hasObject(By.pkg("com.android.documentsui"))) runCatching {
                val r = dev.findObject(By.res("com.android.documentsui:id/icon_thumb"))?.visibleBounds
                t("C: tile bounds=$r")
                if (r != null) for ((x, y) in listOf(r.left + r.width() / 2 to r.bottom - 14, r.right - 10 to r.bottom - 14, r.left + r.width() / 2 to r.top + r.height() / 2)) {
                    if (!picked && dev.hasObject(By.pkg("com.android.documentsui"))) { dev.click(x, y); t("C: tapped $x,$y"); if (closed()) picked = true else t("C: picker still open") }
                }
            }.onFailure { t("C failed: ${it.javaClass.simpleName}") }
            dev.takeScreenshot(File(ci, "picker_2b_after_attempts.png"))
        }
        t("picked: $picked")
        dev.wait(Until.hasObject(By.pkg(ctx.packageName)), 10000)
        Thread.sleep(2500)
        dev.takeScreenshot(File(ci, "picker_3_after.png"))
        val after = Store(ctx.filesDir).load()
        t("photos before=$before after=${after.photos.size}")
        assertTrue("a photo was added through the system picker", after.photos.size == before + 1)
        val p = after.photos.last()
        val f = File(File(ctx.filesDir, "photos"), p.file)
        t("copied file exists=${f.exists()} bytes=${f.length()}")
        assertTrue(f.exists() && f.length() > 0)
    }
}
