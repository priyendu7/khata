package com.openhand.khata.sms.ingest

import javax.inject.Inject

/**
 * Imports past bank SMS from the inbox (PRD feature 7): every one since the chosen date goes
 * through [SmsIngestor], so running it again only adds what's new.
 */
class InboxScanner @Inject constructor(
    private val inbox: SmsInbox,
    private val ingestor: SmsIngestor
) {
    /** [onProgress] gets (done, total) as it goes. */
    suspend fun scan(
        since: Long,
        now: () -> Long = System::currentTimeMillis,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ScanSummary {
        val parser = ingestor.parser()
        val messages = inbox.bankMessages(since, parser::isKnownSender)
        val counts = IngestOutcome.entries.associateWithTo(mutableMapOf()) { 0 }
        onProgress(0, messages.size)
        messages.forEachIndexed { index, message ->
            val outcome =
                ingestor.ingest(message.sender, message.body, message.receivedAt, parser)
            counts[outcome] = counts.getValue(outcome) + 1
            onProgress(index + 1, messages.size)
        }
        val forReview = counts.getValue(IngestOutcome.RECORDED_FOR_REVIEW)
        return ScanSummary(
            since = since,
            finishedAt = now(),
            recorded = counts.getValue(IngestOutcome.RECORDED) + forReview,
            toReview = forReview,
            alreadyThere = counts.getValue(IngestOutcome.ALREADY_THERE),
            unreadable = counts.getValue(IngestOutcome.UNREADABLE)
        )
    }
}
