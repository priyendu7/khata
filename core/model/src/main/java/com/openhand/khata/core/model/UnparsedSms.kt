package com.openhand.khata.core.model

/** A bank SMS with an amount that no parser rule could read, waiting in To review. */
data class UnparsedSms(val id: Long, val sender: String, val body: String, val receivedAt: Long)
