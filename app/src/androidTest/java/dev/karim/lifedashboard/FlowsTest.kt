package dev.karim.lifedashboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.regex.Pattern
import java.time.LocalDate

/** Drives the real system share sheet, save-file dialog, print dialog and restore picker on the emulator. Every step is logged; results go to files/ci/flows_trace.txt. */
class FlowsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private val DOCS = "com.android.documentsui"

    @Test fun shareSaveRestorePrint() {
        val ci = File(ctx.filesDir, "ci").also { it.mkdirs() }
        val trace = StringBuilder()
        fun t(s: String) { trace.appendLine(s); File(ci, "flows_trace.txt").writeText(trace.toString()) }
        val dev = UiDevice.getInstance(inst)
        fun shot(n: String) = runCatching { dev.takeScreenshot(File(ci, "flow_$n.png")) }
        fun shell(c: String): String = runCatching { dev.executeShellCommand(c).trim() }.getOrDefault("shell failed")
        fun a11yClickText(pattern: String): Boolean {
            val root = inst.uiAutomation.rootInActiveWindow ?: return false
            var n = root.findAccessibilityNodeInfosByText(pattern).firstOrNull() ?: return false
            var d = 0
            while (d < 8) {
                if (n.isClickable && n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
                n = n.parent ?: return false; d++
            }
            return false
        }
        fun waitSaveEnabled() { dev.wait(Until.findObject(By.pkg(DOCS).text(Pattern.compile("(?i)save|حفظ")).enabled(true)), 10000); Thread.sleep(500) }
        fun docsOpen() = dev.hasObject(By.pkg(DOCS))
        fun waitDocs(ms: Long = 15000) = dev.wait(Until.hasObject(By.pkg(DOCS)), ms)
        fun waitGone() { dev.wait(Until.gone(By.pkg(DOCS)), 8000); dev.waitForIdle(); Thread.sleep(1200) }
        // click an element on the documentsui screen by text, try object click then accessibility click; returns true when the picker closed
        fun clickToClose(label: String, text: Pattern): Boolean {
            val tries = listOf<() -> Unit>(
                { dev.findObject(By.text(text))?.click() },
                { a11yClickText(text.pattern()) },
            )
            for ((i, f) in tries.withIndex()) {
                if (!docsOpen()) return true
                runCatching { f() }.onFailure { t("$label try$i failed: ${it.javaClass.simpleName}") }
                dev.waitForIdle(); Thread.sleep(1500)
                if (!docsOpen()) { t("$label closed picker with try$i"); return true }
            }
            return !docsOpen()
        }
        val store = Store(ctx.filesDir)
        val today = LocalDate.now()
        val notes = listOf("مشيت نص ساعة", "Short run, felt good", "تعبان شوية", "كتبت فكرة جديدة")
        val days = LinkedHashMap<String, Day>()
        for (i in 0 until 12) {
            val ans = HashMap<Int, Int>(); for (a in 0 until 8) { val v = (i * 3 + a * 5) % 7; if (v < 3) ans[a] = DONE else if (v < 5) ans[a] = NOTDONE }
            val nt = HashMap<Int, String>(); nt[i % 8] = notes[i % 4]
            val hrs = HashMap<Int, String>(); hrs[9] = "شغل"; hrs[20] = "تمرين"
            days[today.minusDays(i.toLong()).toString()] = Day(ans, nt, if (i % 4 == 0) 200.0 + i else null, if (i % 3 == 0) 45.5 + i else null, hrs)
        }
        val A = AppData(days = days, lang = "ar").copy(opening = Opening(1234.0, "2026-01-01"))
        store.save(A)
        rule.activityRule.scenario.recreate(); rule.waitForIdle()
        rule.onAllNodesWithText(tr("tab4")).onLast().performClick(); Thread.sleep(800)
        rule.onNodeWithText(tr("share")).performScrollTo(); Thread.sleep(400)

        // 1. share sheet
        rule.onNodeWithText(tr("share")).performClick()
        val chooser = dev.wait(Until.hasObject(By.text(tr("share_sheet"))), 8000) || dev.hasObject(By.pkg("android")) || dev.hasObject(By.pkg("com.android.intentresolver"))
        Thread.sleep(800); shot("1_share"); t("SHARE chooser visible=$chooser pkg-now=${dev.currentPackageName}")
        dev.pressBack(); dev.waitForIdle(); Thread.sleep(800)

        // 2. save as text file
        rule.onNodeWithText(tr("save_file")).performScrollTo().performClick()
        t("SAVE_FILE documentsui shown=${waitDocs()}")
        waitSaveEnabled(); shot("2_save_dialog")
        val saved = clickToClose("SAVE_FILE save button", Pattern.compile("(?i)save|حفظ"))
        t("SAVE_FILE picker closed=$saved")
        Thread.sleep(1500)
        t("SAVE_FILE files on device: " + shell("find /sdcard/ -name 'life-dashboard-review*' -size +0c 2>/dev/null"))
        t("SAVE_FILE content head: " + shell("head -c 400 \$(find /sdcard/ -name 'life-dashboard-review*' 2>/dev/null | head -1)").replace("\n", " | "))

        // 3. save backup, change data, restore through the picker
        rule.onNodeWithText(tr("save_bk")).performScrollTo().performClick()
        t("SAVE_BACKUP documentsui shown=${waitDocs()}")
        waitSaveEnabled(); shot("3_backup_dialog")
        val bsaved = clickToClose("SAVE_BACKUP save button", Pattern.compile("(?i)save|حفظ"))
        Thread.sleep(2000)
        val bf = shell("find /sdcard/ -name 'life-dashboard-backup*' -size +0c 2>/dev/null")
        t("SAVE_BACKUP picker closed=$bsaved files=$bf size=" + shell("ls -l \$(find /sdcard/ -name 'life-dashboard-backup*' 2>/dev/null | head -1)"))
        store.save(A.copy(opening = Opening(99.0, "2026-02-02")))
        t("RESTORE data changed to opening=" + store.load().opening)
        rule.onNodeWithText(tr("restore")).performScrollTo().performClick()
        t("RESTORE documentsui shown=${waitDocs()}")
        shot("4_restore_picker")
        val rpicked = clickToClose("RESTORE pick file", Pattern.compile("life-dashboard-backup.*"))
        t("RESTORE picker closed=$rpicked")
        Thread.sleep(2500); shot("5_after_restore")
        val after = store.load().opening.amount
        t("RESTORE opening after=$after (expect 1234.0 if restored)")

        // 4. print / PDF: discover the print UI from its own hierarchy, choose "Save as PDF", press the print button, save the file
        runCatching {
            val SP = "com.android.printspooler"
            fun dump(n: String) = File(ci, "print_$n.xml").outputStream().use { dev.dumpWindowHierarchy(it) }
            rule.onNodeWithText(tr("print_pdf")).performScrollTo().performClick()
            val pr = dev.wait(Until.hasObject(By.pkg(SP)), 20000)
            Thread.sleep(2500); shot("6_print"); dump("1_open"); t("PRINT printspooler shown=$pr")
            val res = dev.findObjects(By.pkg(SP)).flatMap { o -> listOfNotNull(o.resourceName) }.distinct()
            t("PRINT resource ids: " + res.joinToString(","))
            // destination spinner
            val spin = dev.findObject(By.res("$SP:id/destination_spinner")) ?: dev.findObject(By.textContains("Select a printer")) ?: dev.findObject(By.descContains("printer"))
            t("PRINT spinner found=${spin != null}")
            spin?.click(); dev.waitForIdle(); Thread.sleep(1500); shot("7_print_destinations"); dump("2_dest")
            t("PRINT texts in dropdown: " + dev.findObjects(By.pkg(SP).textContains("")).mapNotNull { it.text }.distinct().joinToString(" | "))
            val pdfOpt = dev.findObject(By.text(Pattern.compile("(?i).*save as pdf.*")))
            t("PRINT 'Save as PDF' option found=${pdfOpt != null}")
            pdfOpt?.click(); dev.waitForIdle(); Thread.sleep(2000); shot("8_print_pdf_selected"); dump("3_selected")
            t("PRINT resource ids after selecting: " + dev.findObjects(By.pkg(SP)).flatMap { o -> listOfNotNull(o.resourceName) }.distinct().joinToString(","))
            val btn = dev.findObject(By.res("$SP:id/print_button")) ?: dev.findObject(By.desc(Pattern.compile("(?i).*(print|save|download).*")).pkg(SP))
            t("PRINT button found=${btn != null} desc=${btn?.contentDescription}")
            btn?.click(); dev.wait(Until.hasObject(By.pkg(DOCS)), 10000); waitSaveEnabled(); shot("9_pdf_save_dialog")
            t("PRINT save dialog docsOpen=${docsOpen()}")
            if (docsOpen()) { val ok = clickToClose("PRINT save pdf", Pattern.compile("(?i)save|حفظ")); Thread.sleep(3000); t("PRINT pdf dialog closed=$ok") }
            t("PRINT pdf files: " + shell("find /sdcard/ -name '*.pdf' -size +0c 2>/dev/null | head -3"))
        }.onFailure { t("PRINT failed: ${it.javaClass.simpleName} ${it.message}") }
        store.save(AppData())
        // hard checks only where the app state is directly observable
        assertEquals("restore through the system picker brought back the saved data", 1234.0, after ?: -1.0, 0.0)
    }
}
