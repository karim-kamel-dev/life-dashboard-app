package dev.karim.lifedashboard

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Codec {
    fun encode(d: AppData): String {
        val o = JSONObject()
        o.put("v", 1)
        val days = JSONObject()
        d.days.forEach { (k, day) ->
            if (day.isEmpty()) return@forEach
            val j = JSONObject()
            j.put("a", JSONObject().also { a -> day.answers.forEach { (i, v) -> a.put(i.toString(), v) } })
            j.put("n", JSONObject().also { a -> day.notes.forEach { (i, v) -> if (v.isNotBlank()) a.put(i.toString(), v) } })
            j.put("h", JSONObject().also { a -> day.hours.forEach { (i, v) -> if (v.isNotBlank()) a.put(i.toString(), v) } })
            day.income?.let { j.put("in", it) }
            day.expense?.let { j.put("ex", it) }
            days.put(k, j)
        }
        o.put("days", days)
        o.put("photos", JSONArray().also { arr -> d.photos.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("file", p.file).put("date", p.date).put("act", p.activity).put("cap", p.caption)) } })
        o.put("open", JSONObject().also { j -> d.opening.amount?.let { j.put("amt", it) }; d.opening.date?.let { j.put("date", it) } })
        o.put("palette", d.palette).put("mode", d.mode).put("lang", d.lang)
        d.accent?.let { o.put("accent", it) }
        return o.toString()
    }

    fun decode(s: String): AppData {
        val o = JSONObject(s)
        val days = HashMap<String, Day>()
        val dj = o.optJSONObject("days") ?: JSONObject()
        for (k in dj.keys()) {
            val j = dj.getJSONObject(k)
            fun ints(n: String) = j.optJSONObject(n)?.let { a -> a.keys().asSequence().associate { it.toInt() to a.getInt(it) } } ?: emptyMap()
            fun strs(n: String) = j.optJSONObject(n)?.let { a -> a.keys().asSequence().associate { it.toInt() to a.getString(it) } } ?: emptyMap()
            days[k] = Day(ints("a"), strs("n"), if (j.has("in")) j.getDouble("in") else null, if (j.has("ex")) j.getDouble("ex") else null, strs("h"))
        }
        val photos = ArrayList<Photo>()
        o.optJSONArray("photos")?.let { for (i in 0 until it.length()) { val p = it.getJSONObject(i); photos.add(Photo(p.getString("id"), p.getString("file"), p.getString("date"), p.getInt("act"), p.optString("cap", ""))) } }
        val op = o.optJSONObject("open")
        val opening = Opening(if (op != null && op.has("amt")) op.getDouble("amt") else null, op?.optString("date")?.takeIf { it.isNotEmpty() })
        return AppData(days, photos, opening, o.optString("palette", "olive"), o.optString("mode", "dark"), if (o.has("accent")) o.getLong("accent") else null, o.optString("lang", "auto"))
    }
}

class Store(val dir: File) {
    val photosDir = File(dir, "photos").also { it.mkdirs() }
    private val file = File(dir, "data.json")
    fun load(): AppData = try { if (file.exists()) Codec.decode(file.readText()) else AppData() } catch (e: Exception) { AppData() }
    /** Write to a temp file then rename, so a crash mid-write cannot leave a half-written data file. */
    fun save(d: AppData) {
        val tmp = File(dir, "data.json.tmp")
        tmp.writeText(Codec.encode(d))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
