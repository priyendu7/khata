package com.openhand.khata.feature.csv

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the files the user picks in the system file picker (Storage Access Framework),
 * so Khata needs no storage permission. Everything is UTF-8.
 */
class CsvFiles @Inject constructor(@ApplicationContext private val context: Context) {
    /** @throws IOException if the file can't be written. */
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Can't open $uri")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    /** @throws IOException if the file can't be read. */
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Can't open $uri")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
