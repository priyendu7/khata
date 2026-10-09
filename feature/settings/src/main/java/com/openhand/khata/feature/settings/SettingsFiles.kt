package com.openhand.khata.feature.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the settings file where the user picks in the system file picker (Storage
 * Access Framework), like `CsvFiles` does for CSV, so Khata needs no storage permission.
 */
class SettingsFiles @Inject constructor(@ApplicationContext private val context: Context) {
    /** @throws IOException if the file can't be written. */
    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Can't open $uri")
        stream.use { it.write(bytes) }
    }

    /** @throws IOException if the file can't be read, or is far larger than a settings file. */
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Can't open $uri")
        val out = ByteArrayOutputStream()
        stream.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = it.read(buffer)
            while (read >= 0) {
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) throw IOException("Too large: $uri")
                read = it.read(buffer)
            }
        }
        out.toByteArray()
    }

    private companion object {
        /** Thousands of rules and payees come to well under a megabyte. */
        const val MAX_BYTES = 16 * 1024 * 1024
    }
}
