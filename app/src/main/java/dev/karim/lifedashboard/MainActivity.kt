package dev.karim.lifedashboard

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import kotlinx.coroutines.sync.withLock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import java.io.File
import java.time.LocalDate
import java.util.UUID

class Palette(val name: String, val label: String, val accent: Color, val darkTop: Color, val darkBot: Color, val lightTop: Color, val lightBot: Color)

val PALETTES = listOf(
    Palette("olive", "pal_olive", Color(0xFF9DBB5A), Color(0xFF2B3A22), Color(0xFF0B120C), Color(0xFFEAF0DC), Color(0xFFC9D6B0)),
    Palette("graphite", "pal_graphite", Color(0xFFC9D1DB), Color(0xFF3A4048), Color(0xFF111418), Color(0xFFFFFFFF), Color(0xFFD5DAE0)),
    Palette("night", "pal_night", Color(0xFF8B7CFF), Color(0xFF232A52), Color(0xFF0A0D1A), Color(0xFFF3F2FB), Color(0xFFD9D6F0)),
    Palette("sunset", "pal_sunset", Color(0xFFF2701D), Color(0xFF4A2A18), Color(0xFF140C08), Color(0xFFFFF4EC), Color(0xFFF1D6BF)),
)
val ACCENTS = listOf(0xFF9DBB5A, 0xFFC9D1DB, 0xFF8B7CFF, 0xFF2D8CFF, 0xFFE5508C, 0xFFE0B100)

class Colors(val bg: Color, val card: Color, val ink: Color, val mut: Color, val acc: Color, val off: Color, val bgBrush: Brush, val edge: Color, val solid: Color) {
    val ok = Color(0xFF1FB878); val no = Color(0xFFE5484D)
}
val LocalC = compositionLocalOf { Colors(Color.Black, Color.DarkGray, Color.White, Color.Gray, Color.Magenta, Color.DarkGray, Brush.verticalGradient(listOf(Color.Black, Color.Black)), Color.Gray, Color.DarkGray) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(filesDir)
        setContent { App(store) }
    }
}

@Composable
fun App(store: Store, initial: AppData? = null, startTab: Int = 0, startDate: LocalDate = LocalDate.now()) {
    var data by remember { mutableStateOf(initial ?: store.load()) }
    var tab by remember { mutableStateOf(startTab) }
    var date by remember { mutableStateOf(startDate) }
    val update: ((AppData) -> AppData) -> Unit = { f -> data = f(data); store.save(data) }
    val sys = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (data.mode) { "dark" -> true; "light" -> false; else -> sys }
    val pal = PALETTES.firstOrNull { it.name == data.palette } ?: PALETTES[0]
    val top = if (dark) pal.darkTop else pal.lightTop; val bot = if (dark) pal.darkBot else pal.lightBot
    val glass = if (dark) Color.White.copy(alpha = .08f) else Color.White.copy(alpha = .62f)
    val c = Colors(
        bg = bot, card = glass,
        ink = if (dark) Color(0xFFF1F3F0) else Color(0xFF1A2230), mut = if (dark) Color(0xFFA3AAA0) else Color(0xFF5E6674),
        acc = (data.accent?.let { Color(it) } ?: pal.accent).let { if (dark) it else androidx.compose.ui.graphics.lerp(it, Color.Black, .45f) }, off = if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .07f),
        bgBrush = Brush.verticalGradient(listOf(top, bot)), edge = if (dark) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .9f),
        solid = glass.compositeOver(bot))
    Lang.cur = Lang.resolve(data.lang)
    CompositionLocalProvider(LocalC provides c, LocalLayoutDirection provides (if (Lang.isRtl()) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
        Column(Modifier.fillMaxSize().background(c.bgBrush).statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayScreen(data, update, date) { date = it }
                    1 -> YearScreen(data, date) { date = it; tab = 0 }
                    2 -> MoneyScreen(data, update, date)
                    3 -> PhotosScreen(store, data, update)
                    else -> SettingsScreen(store, data, update) { data = it }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.card).border(1.dp, c.edge, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).navigationBarsPadding().padding(vertical = 10.dp)) {
                listOf(tr("tab0"), tr("tab1"), tr("tab2"), tr("tab3"), tr("tab4")).forEachIndexed { i, n ->
                    Box(Modifier.weight(1f).clickable { tab = i }.padding(horizontal = 2.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(n, color = if (i == tab) c.acc else c.mut, fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable fun T(s: String, size: Int = 14, color: Color = LocalC.current.ink, bold: Boolean = false, modifier: Modifier = Modifier) =
    Text(s, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = modifier)

@Composable fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(20.dp)).background(LocalC.current.card).border(1.dp, LocalC.current.edge, RoundedCornerShape(20.dp)).padding(14.dp), content = content)

@Composable fun Field(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val c = LocalC.current
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.off).padding(10.dp)) {
        if (value.isEmpty()) T(hint, 13, c.mut)
        BasicTextField(value, onChange, textStyle = TextStyle(color = c.ink, fontSize = 14.sp), cursorBrush = SolidColor(c.acc), modifier = Modifier.fillMaxWidth())
    }
}

@Composable fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalC.current
    Box(Modifier.padding(end = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Brush.horizontalGradient(listOf(c.acc, c.acc.copy(alpha = .7f).compositeOver(Color.White))) else SolidColor(c.off)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)) {
        T(label, 12, if (on) Color(0xFF10140F) else c.ink)
    }
}

@Composable fun Btn(label: String, color: Color? = null, onClick: () -> Unit) {
    val c = LocalC.current
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (color != null) SolidColor(color) else Brush.horizontalGradient(listOf(c.acc, c.acc.copy(alpha = .7f).compositeOver(Color.White)))).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)) { T(label, 13, if (color == null) Color(0xFF10140F) else LocalC.current.ink, true) }
}

@Composable fun Title(t: String, sub: String? = null) = Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
    T(t, 24, bold = true); if (sub != null) T(sub, 12, LocalC.current.mut, modifier = Modifier.padding(top = 4.dp))
}

val ARABIC_DAYS: List<String> get() = tr("days").split(",")
fun dayLabel(d: LocalDate) = "${ARABIC_DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth}/${d.monthValue}/${d.year}"

val decodeLock = kotlinx.coroutines.sync.Mutex()

@Composable fun Thumb(store: File, p: Photo, size: Int, modifier: Modifier = Modifier) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p.file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var r: androidx.compose.ui.graphics.ImageBitmap? = null
            var tries = 0
            while (r == null && tries < 3) {
                r = decodeLock.withLock {
                    try { BitmapFactory.decodeFile(File(store, p.file).path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() } catch (e: Exception) { null }
                }
                if (r == null) { tries++; kotlinx.coroutines.delay(150) }
            }
            r
        }
    }
    Box(modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(LocalC.current.off)) {
        bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

fun copyPhoto(ctx: Context, store: Store, uri: Uri): String? = try {
    val name = UUID.randomUUID().toString() + ".jpg"
    ctx.contentResolver.openInputStream(uri)!!.use { i -> File(store.photosDir, name).outputStream().use { o -> i.copyTo(o) } }
    name
} catch (e: Exception) { null }

@Composable
fun TodayScreen(data: AppData, update: ((AppData) -> AppData) -> Unit, date: LocalDate, setDate: (LocalDate) -> Unit) {
    val c = LocalC.current
    val ctx = LocalContext.current
    val store = remember { Store(ctx.filesDir) }
    val key = date.toString()
    val day = data.days[key] ?: Day()
    var pendingAct by remember { mutableStateOf(0) }
    var hourEdit by remember { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val f = copyPhoto(ctx, store, uri)
            if (f == null) Toast.makeText(ctx, tr("photo_fail"), Toast.LENGTH_SHORT).show()
            else update { d -> d.copy(photos = d.photos + Photo(UUID.randomUUID().toString(), f, key, pendingAct)) }
        }
    }
    fun setDay(f: (Day) -> Day) = update { d -> d.copy(days = d.days + (key to f(d.days[key] ?: Day()))) }
    val answered = day.answers.size
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { setDate(date.minusDays(1)) }.padding(10.dp)) { T(if (Lang.isRtl()) "›" else "‹", 24, c.mut) }
                Column(Modifier.weight(1f)) {
                    T(dayLabel(date), 20, bold = true)
                    T(tr("answered_of", answered, ACTIVITIES.size), 12, c.mut)
                }
                Box(Modifier.clickable { setDate(date.plusDays(1)) }.padding(10.dp)) { T(if (Lang.isRtl()) "‹" else "›", 24, c.mut) }
            }
        }
        items(ACTIVITIES.size) { i ->
            val ans = day.answers[i]
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(ACTIVITIES[i], 16, bold = true, modifier = Modifier.weight(1f))
                    AnswerBtn("✓", ans == DONE, c.ok) { setDay { d -> d.copy(answers = if (ans == DONE) d.answers - i else d.answers + (i to DONE)) } }
                    Spacer(Modifier.width(8.dp))
                    AnswerBtn("✕", ans == NOTDONE, c.no) { setDay { d -> d.copy(answers = if (ans == NOTDONE) d.answers - i else d.answers + (i to NOTDONE)) } }
                }
                Spacer(Modifier.height(8.dp))
                var note by remember(key, i) { mutableStateOf(day.notes[i] ?: "") }
                Field(note, tr("note_hint")) { note = it; setDay { d -> d.copy(notes = d.notes + (i to it)) } }
                val ph = data.photos.filter { it.date == key && it.activity == i }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ph.forEach { Thumb(store.photosDir, it, 52, Modifier.padding(end = 6.dp)) }
                    Chip(tr("add_photo"), false) { pendingAct = i; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
            }
        }
        item {
            Card {
                T(tr("hours_title"), 14, bold = true)
                T(tr("hours_hint"), 11, c.mut)
                Spacer(Modifier.height(8.dp))
                for (r in 0 until 3) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (h in r * 8 until r * 8 + 8) {
                        val has = !day.hours[h].isNullOrBlank()
                        Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp)).background(if (has) c.acc else c.off).clickable { hourEdit = h }, contentAlignment = Alignment.Center) {
                            T("$h", 11, if (has) Color.White else c.mut)
                        }
                    }
                }
            }
        }
    }
    hourEdit?.let { h ->
        var txt by remember(h, key) { mutableStateOf(day.hours[h] ?: "") }
        AlertDialog(onDismissRequest = { hourEdit = null }, containerColor = c.solid,
            title = { T(tr("hour_n", h), 16, bold = true) },
            text = { Field(txt, tr("hour_q")) { txt = it } },
            confirmButton = { TextButton({ setDay { d -> d.copy(hours = d.hours + (h to txt)) }; hourEdit = null }) { T(tr("save"), 14, c.acc, true) } },
            dismissButton = { TextButton({ hourEdit = null }) { T(tr("cancel"), 14, c.mut) } })
    }
}

@Composable fun AnswerBtn(sym: String, on: Boolean, col: Color, onClick: () -> Unit) {
    val c = LocalC.current
    Box(Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) col else c.off).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        T(sym, 18, if (on) Color.White else c.mut, true)
    }
}

@Composable
fun YearScreen(data: AppData, date: LocalDate, openDay: (LocalDate) -> Unit) {
    val c = LocalC.current
    var year by remember { mutableStateOf(date.year) }
    var range by remember { mutableStateOf(1) }
    val to = date
    val from = when (range) { 0 -> to.minusDays(6); 1 -> to.withDayOfMonth(1); else -> LocalDate.of(to.year, 1, 1) }
    val (stats, rec) = remember(data.days, from, to) { activityStats(data.days, from, to) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { year-- }.padding(10.dp)) { T(if (Lang.isRtl()) "›" else "‹", 24, c.mut) }
                T("$year", 24, bold = true, modifier = Modifier.weight(1f))
                Box(Modifier.clickable { year++ }.padding(10.dp)) { T(if (Lang.isRtl()) "‹" else "›", 24, c.mut) }
            }
            T(tr("legend"), 11, c.mut, modifier = Modifier.padding(horizontal = 20.dp))
        }
        item {
            Card {
                val cell = 9.5f
                Canvas(Modifier.fillMaxWidth().height((12 * 15 + 4).dp).testTag("yeargrid").pointerInput(year) {
                    detectTap { off ->
                        val w = size.width.toFloat(); val step = w / 33f
                        val m = (off.y / (15.dp.toPx())).toInt()
                        val dd = ((w - off.x) / step).toInt()
                        if (m in 0..11 && dd in 1..LocalDate.of(year, m + 1, 1).lengthOfMonth()) openDay(LocalDate.of(year, m + 1, dd))
                    }
                }) {
                    val step = size.width / 33f
                    for (m in 0 until 12) {
                        val y = m * 15.dp.toPx()
                        for (d in 1..LocalDate.of(year, m + 1, 1).lengthOfMonth()) {
                            val day = data.days[LocalDate.of(year, m + 1, d).toString()]
                            val (dn, nd) = dayCount(day)
                            val col = when {
                                day == null || day.answers.isEmpty() -> c.off
                                dn == 0 -> c.no
                                else -> c.ok.copy(alpha = .35f + .65f * (dn / ACTIVITIES.size.toFloat()))
                            }
                            val x = size.width - (d + 1) * step
                            drawRect(col, Offset(x, y), Size(step - 3f, 12.dp.toPx()))
                        }
                    }
                }
                T(tr("year_axis"), 10, c.mut)
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Chip(tr("r7"), range == 0) { range = 0 }; Chip(tr("rmonth"), range == 1) { range = 1 }; Chip(tr("ryear"), range == 2) { range = 2 }
            }
        }
        item {
            Card {
                T(tr("freq_t", from, to), 14, bold = true)
                T(tr("rec_note", rec), 11, c.mut)
                Spacer(Modifier.height(8.dp))
                ACTIVITIES.forEachIndexed { i, a ->
                    val s = stats[i]
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row { T(a, 13, modifier = Modifier.weight(1f)); T(tr("stat_line", s.done, s.notDone, s.unanswered) + (s.pct?.let { " • $it%" } ?: ""), 11, c.mut) }
                        val total = (s.done + s.notDone + s.unanswered).coerceAtLeast(1)
                        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.off)) {
                            if (s.done > 0) Box(Modifier.weight(s.done.toFloat()).fillMaxHeight().background(c.ok))
                            if (s.notDone > 0) Box(Modifier.weight(s.notDone.toFloat()).fillMaxHeight().background(c.no))
                            if (s.unanswered > 0) Box(Modifier.weight(s.unanswered.toFloat()).fillMaxHeight().background(Color.Transparent))
                        }
                    }
                }
            }
        }
    }
}

suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTap(onTap: (Offset) -> Unit) =
    detectTapGestures(onTap = onTap)

@Composable
fun MoneyScreen(data: AppData, update: ((AppData) -> AppData) -> Unit, date: LocalDate) {
    val c = LocalC.current
    val key = date.toString()
    val day = data.days[key] ?: Day()
    var openAmt by remember { mutableStateOf(data.opening.amount?.let { fmt(it) } ?: "") }
    var openDate by remember { mutableStateOf(data.opening.date ?: key) }
    var inc by remember(key) { mutableStateOf(day.income?.let { fmt(it) } ?: "") }
    var exp by remember(key) { mutableStateOf(day.expense?.let { fmt(it) } ?: "") }
    val incOk = parseAmount(inc).second; val expOk = parseAmount(exp).second
    val openOk = parseAmount(openAmt).second && runCatching { LocalDate.parse(openDate) }.isSuccess
    val rows = remember(data.days, data.opening) { moneyRows(data.days, data.opening) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item { Title(tr("tab2"), tr("money_sub")) }
        item {
            Card {
                T(tr("calc_bal"), 12, c.mut)
                val last = rows.lastOrNull()?.balance ?: data.opening.amount
                T(last?.let { fmt(it) + " " + tr("cur") } ?: NO_DATA, 28, bold = true)
                T(data.opening.amount?.let { tr("open_line", fmt(it), data.opening.date ?: "—") } ?: tr("no_open"), 11, c.mut)
            }
            Card {
                T(tr("open_bal"), 14, bold = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(openAmt, tr("amount"), Modifier.weight(1f)) { openAmt = it }
                    Field(openDate, "yyyy-mm-dd", Modifier.weight(1f)) { openDate = it }
                }
                if (!openOk) T(tr("bad_open"), 11, c.no)
                Spacer(Modifier.height(8.dp))
                Btn(tr("save_open")) { if (openOk) update { d -> d.copy(opening = Opening(parseAmount(openAmt).first, openDate)) } }
            }
            Card {
                T(tr("move_of", dayLabel(date)), 14, bold = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Field(inc, tr("income_h"), Modifier.weight(1f)) { inc = it }
                    Field(exp, tr("expense"), Modifier.weight(1f)) { exp = it }
                }
                if (!incOk || !expOk) T(tr("bad_amt"), 11, c.no)
                Spacer(Modifier.height(8.dp))
                Btn(tr("save_day")) {
                    if (incOk && expOk) update { d -> d.copy(days = d.days + (key to (d.days[key] ?: Day()).copy(income = parseAmount(inc).first, expense = parseAmount(exp).first))) }
                }
            }
        }
        item {
            Card {
                T(tr("rec_days"), 14, bold = true)
                if (rows.isEmpty()) T(tr("no_money_days"), 12, c.mut)
                rows.takeLast(31).reversed().forEach { r ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        T(r.date, 12, c.mut)
                        T(tr("row_line", r.income?.let { fmt(it) } ?: NO_DATA, r.expense?.let { fmt(it) } ?: NO_DATA, fmt(r.change), r.balance?.let { fmt(it) } ?: NO_DATA), 12)
                    }
                }
                val pts = rows.mapNotNull { it.balance }
                if (pts.size >= 2) {
                    Spacer(Modifier.height(10.dp))
                    T(tr("bal_line"), 12, c.mut)
                    Canvas(Modifier.fillMaxWidth().height(90.dp).padding(top = 6.dp)) {
                        val mn = pts.min(); val mx = pts.max(); val span = (mx - mn).takeIf { it > 0 } ?: 1.0
                        val p = Path()
                        pts.forEachIndexed { i, v ->
                            val x = size.width - size.width * i / (pts.size - 1)
                            val y = (size.height * (1 - (v - mn) / span)).toFloat()
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        drawPath(p, c.acc, style = Stroke(width = 5f))
                    }
                }
            }
        }
    }
}

@Composable
fun PhotosScreen(store: Store, data: AppData, update: ((AppData) -> AppData) -> Unit) {
    val c = LocalC.current
    var filter by remember { mutableStateOf(-1) }
    var open by remember { mutableStateOf<Photo?>(null) }
    val list = data.photos.filter { filter < 0 || it.activity == filter }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize()) {
        Title(tr("lib"), tr("lib_sub", data.photos.size))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Chip(tr("all"), filter < 0) { filter = -1 }
            ACTIVITIES.forEachIndexed { i, a -> Chip(a, filter == i) { filter = i } }
        }
        if (list.isEmpty()) T(tr("no_photos"), 13, c.mut, modifier = Modifier.padding(20.dp))
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { p ->
                Box(Modifier.clickable { open = p }) {
                    Thumb(store.photosDir, p, 110, Modifier.fillMaxWidth().aspectRatio(1f))
                    Box(Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp)) { T(ACTIVITIES[p.activity], 10, Color.White) }
                }
            }
        }
    }
    open?.let { p ->
        var cap by remember(p.id) { mutableStateOf(p.caption) }
        AlertDialog(onDismissRequest = { open = null }, containerColor = c.solid,
            title = { T("${ACTIVITIES[p.activity]} • ${p.date}", 15, bold = true) },
            text = {
                Column {
                    val bmp = remember(p.id) { BitmapFactory.decodeFile(File(store.photosDir, p.file).path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }
                    bmp?.let { Image(it, null, Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = ContentScale.Fit) }
                    Spacer(Modifier.height(8.dp))
                    Field(cap, tr("cap_hint")) { cap = it }
                }
            },
            confirmButton = { TextButton({ update { d -> d.copy(photos = d.photos.map { if (it.id == p.id) it.copy(caption = cap) else it }) }; open = null }) { T(tr("save"), 14, c.acc, true) } },
            dismissButton = { TextButton({ File(store.photosDir, p.file).delete(); update { d -> d.copy(photos = d.photos.filter { it.id != p.id }) }; open = null }) { T(tr("del_photo"), 14, c.no) } })
    }
}

@Composable
fun SettingsScreen(store: Store, data: AppData, update: ((AppData) -> AppData) -> Unit, replace: (AppData) -> Unit) {
    val c = LocalC.current
    val ctx = LocalContext.current
    var range by remember { mutableStateOf(1) }
    var secs by remember { mutableStateOf(setOf("freq", "details", "money", "question")) }
    var exportText by remember { mutableStateOf("") }
    var excluded by remember { mutableStateOf(setOf<String>()) }
    val today = LocalDate.now()
    fun rangeFrom() = when (range) { 0 -> today.minusDays(6); 1 -> today.withDayOfMonth(1); else -> LocalDate.of(today.year, 1, 1) }
    fun inRange() = data.photos.filter { it.date >= rangeFrom().toString() && it.date <= today.toString() }.sortedBy { it.date }
    fun chosen(): Set<String> = inRange().map { it.id }.toSet() - excluded
    fun build(): String = buildExport(data, rangeFrom(), today, secs - "photos")
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u ->
        if (u != null) ctx.contentResolver.openOutputStream(u)?.use { it.write(exportText.toByteArray()) }
    }
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { u ->
        if (u != null) ctx.contentResolver.openOutputStream(u)?.use { it.write(exportText.toByteArray()) }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        if (u != null) ctx.contentResolver.openOutputStream(u)?.use { Backup.write(store, data, it) }
        Toast.makeText(ctx, tr("backup_ok"), Toast.LENGTH_SHORT).show()
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            val r = try { ctx.contentResolver.openInputStream(u)?.use { Backup.restore(store, it) } } catch (e: Exception) { null }
            if (r != null) { replace(r); Toast.makeText(ctx, tr("restored"), Toast.LENGTH_SHORT).show() }
            else Toast.makeText(ctx, tr("bad_backup"), Toast.LENGTH_LONG).show()
        }
    }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item { Title(tr("tab4")) }
        item {
            Card {
                T(tr("language"), 14, bold = true)
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(tr("system"), data.lang == "auto") { update { it.copy(lang = "auto") } }
                    Lang.list.forEach { (code, name) -> Chip(name, data.lang == code) { update { it.copy(lang = code) } } }
                }
            }
        }
        item {
            Card {
                T(tr("theme"), 14, bold = true)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PALETTES.forEach { p ->
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(p.accent).border(if (p.name == data.palette) 3.dp else 0.dp, c.ink, RoundedCornerShape(14.dp)).clickable { update { d -> d.copy(palette = p.name, accent = null) } })
                    }
                }
                Spacer(Modifier.height(12.dp)); T(tr("mode"), 13, c.mut)
                Row(Modifier.padding(top = 6.dp)) { Chip(tr("dark"), data.mode == "dark") { update { it.copy(mode = "dark") } }; Chip(tr("light"), data.mode == "light") { update { it.copy(mode = "light") } }; Chip(tr("system"), data.mode == "system") { update { it.copy(mode = "system") } } }
                T(tr("accent_note"), 13, c.mut)
                var hex by remember { mutableStateOf("") }
                val hv = parseHexColor(hex)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(hex, tr("hex_hint"), Modifier.weight(1f)) { hex = it }
                    Btn(tr("apply")) { if (hv != null) update { it.copy(accent = hv) } }
                }
                if (hex.isNotEmpty() && hv == null) T(tr("hex_bad"), 11, c.no)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEach { a -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Color(a)).clickable { update { it.copy(accent = a) } }) }
                }
            }
            Card {
                T(tr("sheet_t"), 14, bold = true)
                T(tr("sheet_note"), 11, c.mut)
                Row(Modifier.padding(top = 8.dp)) { Chip(tr("r7"), range == 0) { range = 0 }; Chip(tr("rmonth"), range == 1) { range = 1 }; Chip(tr("ryear"), range == 2) { range = 2 } }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("freq" to tr("sec_freq"), "details" to tr("sec_details"), "money" to tr("tab2"), "photos" to tr("sec_photos"), "question" to tr("sec_q")).forEach { (k, l) ->
                        Chip(l, k in secs) { secs = if (k in secs) secs - k else secs + k }
                    }
                }
                if ("photos" in secs) {
                    val rp = inRange()
                    T(tr("pick_photos", rp.count { it.id !in excluded }, rp.size), 11, c.mut, modifier = Modifier.padding(top = 8.dp))
                    if (rp.isNotEmpty()) {
                        Row { Chip(tr("pick_all"), excluded.isEmpty()) { excluded = emptySet() }; Chip(tr("pick_none"), excluded.size >= rp.size) { excluded = rp.map { it.id }.toSet() } }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rp.forEach { ph ->
                                val on = ph.id !in excluded
                                Box(Modifier.testTag("selphoto_${ph.id}").clickable { excluded = if (on) excluded + ph.id else excluded - ph.id }) {
                                    Thumb(store.photosDir, ph, 64)
                                    if (!on) Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xAA000000)))
                                    T(if (on) "✓" else "", 16, c.acc, true, Modifier.align(Alignment.TopEnd).padding(4.dp))
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(tr("share")) {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, build()) }
                        ctx.startActivity(Intent.createChooser(i, tr("share_sheet")))
                    }
                    Btn(tr("save_file")) { exportText = build(); saveText.launch("life-dashboard-review.txt") }
                    Btn(tr("print_pdf")) { printHtml(ctx, buildHtmlDoc(store, data, build(), rangeFrom(), today, "photos" in secs, chosen())) }
                }
                Row(Modifier.padding(top = 8.dp)) {
                    Btn(tr("save_html")) { exportText = buildHtmlDoc(store, data, build(), rangeFrom(), today, "photos" in secs, chosen()); saveHtml.launch("life-dashboard-review.html") }
                }
                T(tr("sheet_foot"), 11, c.mut, modifier = Modifier.padding(top = 6.dp))
            }
            Card {
                T(tr("backup_t"), 14, bold = true)
                T(tr("backup_note"), 11, c.mut)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(tr("save_bk")) { saveBackup.launch("life-dashboard-backup.zip") }
                    Btn(tr("restore"), c.off) { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                }
            }
        }
    }
}

var printView: WebView? = null

fun photoDataUri(store: Store, p: Photo): String? = try {
    val f = File(store.photosDir, p.file)
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var ss = 1; while (o.outWidth / ss > 900) ss *= 2
    val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss })
    val bo = java.io.ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, bo)
    "data:image/jpeg;base64," + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP)
} catch (e: Exception) { null }

fun buildHtmlDoc(store: Store, data: AppData, text: String, from: LocalDate, to: LocalDate, withPhotos: Boolean, onlyIds: Set<String>? = null): String {
    val sb = StringBuilder("<html dir='${if (Lang.isRtl()) "rtl" else "ltr"}' lang='${Lang.cur}'><head><meta charset='utf-8'></head><body style='font-family:sans-serif;font-size:14px'><pre style='white-space:pre-wrap;font-family:inherit'>")
    sb.append(htmlEscape(text)).append("</pre>")
    if (withPhotos) {
        val ph = data.photos.filter { it.date >= from.toString() && it.date <= to.toString() && (onlyIds == null || it.id in onlyIds) }.sortedBy { it.date }
        sb.append("<h3>${tr("sec_photos_h", ph.size)}</h3>")
        if (ph.isEmpty()) sb.append("<p>${tr("html_nophotos")}</p>")
        ph.forEach { p ->
            val uri = photoDataUri(store, p)
            sb.append("<div style='page-break-inside:avoid;margin:10px 0'>")
            if (uri != null) sb.append("<img src='$uri' style='max-width:48%;max-height:260px'>") else sb.append("<i>${tr("photo_missing")}</i>")
            sb.append("<div>${htmlEscape(ACTIVITIES[p.activity])} • ${p.date}${if (p.caption.isNotBlank()) " • " + htmlEscape(p.caption) else ""}</div></div>")
        }
    }
    return sb.append("</body></html>").toString()
}
fun htmlEscape(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

fun printHtml(ctx: Context, html: String) {
    val wv = WebView(ctx); printView = wv
    wv.webViewClient = object : WebViewClient() {
        override fun onPageFinished(v: WebView, url: String?) {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print("Life Dashboard", v.createPrintDocumentAdapter("Life Dashboard"), null)
        }
    }
    wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

fun parseHexColor(t: String): Long? {
    val m = Regex("^#?([0-9a-fA-F]{6})$").matchEntire(t.trim()) ?: return null
    return 0xFF000000L or m.groupValues[1].toLong(16)
}
