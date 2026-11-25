package dev.karim.lifedashboard

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Backup {
    fun write(store: Store, data: AppData, out: OutputStream) {
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("data.json")); z.write(Codec.encode(data).toByteArray()); z.closeEntry()
            data.photos.forEach { p ->
                val f = File(store.photosDir, p.file)
                if (f.exists()) { z.putNextEntry(ZipEntry("photos/${p.file}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
        }
    }

    /** Reads everything into memory first; only replaces current data if data.json parses. Returns the restored data or null. */
    fun restore(store: Store, input: InputStream): AppData? {
        var json: String? = null
        val pics = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val bytes = z.readBytes()
                if (e.name == "data.json") json = String(bytes)
                else if (e.name.startsWith("photos/")) pics[e.name.removePrefix("photos/").substringAfterLast('/')] = bytes
            }
        }
        val data = try { Codec.decode(json ?: return null) } catch (e: Exception) { return null }
        pics.forEach { (n, b) -> File(store.photosDir, n).writeBytes(b) }
        store.save(data)
        return data
    }
}
