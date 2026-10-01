package com.openhand.khata.sms.ingest

import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads the phone's SMS inbox. Needs READ_SMS. */
class SmsInbox @Inject constructor(@ApplicationContext private val context: Context) {
    data class Message(val sender: String, val body: String, val receivedAt: Long)

    /**
     * SMS received since [since] (epoch millis) from senders [accepts] lets through (businesses,
     * never people), oldest first. The sender is checked first, so no one else's message text is
     * ever read.
     */
    fun businessMessages(since: Long, accepts: (String) -> Boolean): List<Message> {
        val columns = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            columns,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return emptyList()
        return cursor.use {
            val address = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            buildList {
                while (it.moveToNext()) {
                    val sender = it.getString(address).orEmpty()
                    if (accepts(sender)) {
                        add(Message(sender, it.getString(body).orEmpty(), it.getLong(date)))
                    }
                }
            }
        }
    }
}
