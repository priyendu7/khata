package com.openhand.khata.sms.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One part of an SMS as the phone delivers it; long SMS arrive in several. */
data class SmsPart(val sender: String, val body: String, val receivedAt: Long)

/**
 * Joins the parts of long SMS: parts from the same sender in one delivery are one message, in the
 * order they came, timed by the first part.
 */
internal fun joinParts(parts: List<SmsPart>): List<SmsInbox.Message> =
    parts.groupBy { it.sender }.map { (sender, pieces) ->
        SmsInbox.Message(sender, pieces.joinToString("") { it.body }, pieces.first().receivedAt)
    }

/**
 * Records new bank SMS as they arrive (PRD feature 7), through the same [SmsIngestor] as the inbox
 * import, so an SMS both of them see is saved once.
 */
class NewSmsHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SmsImportSettings,
    private val ingestor: SmsIngestor
) {
    suspend fun handle(parts: List<SmsPart>): List<IngestOutcome> {
        if (!settings.enabled.value || !context.hasSmsPermissions()) return emptyList()
        // The sender is checked before the text is used, so no one else's SMS is ever read.
        return joinParts(parts.filter { ingestor.isBankSender(it.sender) })
            .map { ingestor.ingest(it.sender, it.body, it.receivedAt) }
    }

    private fun Context.hasSmsPermissions() =
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
