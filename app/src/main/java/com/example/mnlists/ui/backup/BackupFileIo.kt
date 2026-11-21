package com.example.mnlists.ui.backup

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Storage Access Framework file I/O for a backup document. */
object BackupFileIo {

    private const val MAX_FILE_BYTES = 16 * 1024 * 1024

    fun suggestedFileName(): String {
        val now = LocalDateTime.now()
        return "mnlists-backup-%04d%02d%02d-%02d%02d.json".format(
            now.year,
            now.monthValue,
            now.dayOfMonth,
            now.hour,
            now.minute,
        )
    }

    /** Returns null when the document is missing, unreadable or unreasonably large. */
    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8 * 1024)
                val text = StringBuilder()
                var read: Int
                while (reader.read(buffer).also { read = it } != -1) {
                    if (text.length + read > MAX_FILE_BYTES) return@withContext null
                    text.append(buffer, 0, read)
                }
                text.toString()
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    suspend fun write(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(uri) ?: return@withContext false
            output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
