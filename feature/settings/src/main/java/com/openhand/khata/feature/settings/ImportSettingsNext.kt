package com.openhand.khata.feature.settings

/** Where the result of an import can send the user next. */
data class ImportSettingsNext(
    val onSmsImport: () -> Unit = {},
    val onAppLock: () -> Unit = {},
    val onImportTransactions: () -> Unit = {}
)
