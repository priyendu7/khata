package com.openhand.khata.sms.ingest

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony

/**
 * Stands in for the phone's SMS inbox (`content://sms`). Records which messages' text was read,
 * so tests can check that other people's SMS never are.
 */
class FakeSmsInbox : ContentProvider() {
    data class Sms(val sender: String, val body: String, val date: Long)

    companion object {
        val messages = mutableListOf<Sms>()
        val bodiesRead = mutableListOf<String>()

        fun reset() {
            messages.clear()
            bodiesRead.clear()
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        require(uri == Telephony.Sms.Inbox.CONTENT_URI) { "Unexpected $uri" }
        require(selection == "${Telephony.Sms.DATE} >= ?") { "Unexpected selection $selection" }
        val since = selectionArgs!!.single().toLong()
        val rows = messages.filter { it.date >= since }.sortedBy { it.date }
        val columns = requireNotNull(projection)
        return object : MatrixCursor(columns) {
            override fun getString(column: Int): String {
                if (columns[column] == Telephony.Sms.BODY) bodiesRead += rows[position].sender
                return super.getString(column)
            }
        }.apply {
            rows.forEach { sms ->
                addRow(
                    columns.map {
                        when (it) {
                            Telephony.Sms.ADDRESS -> sms.sender
                            Telephony.Sms.BODY -> sms.body
                            Telephony.Sms.DATE -> sms.date
                            else -> null
                        }
                    }
                )
            }
        }
    }

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ) = 0
}
