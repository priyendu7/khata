package com.openhand.khata.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFormatTest {
    private fun error(json: String): SettingsFileError? = try {
        SettingsFormat.decode(json.toByteArray())
        null
    } catch (e: SettingsFileException) {
        e.error
    }

    @Test
    fun roundTrip() {
        val file = SettingsFile(
            exportedAt = "2026-10-09T08:30:00Z",
            appVersion = "0.9.0",
            customParsers = listOf(CustomParserData("khata1:abc", enabled = false)),
            accounts = listOf(AccountData("HDFC savings", "bank", "HDFC", "1234")),
            events = listOf(EventData("Goa trip", "2026-10-01", "2026-10-05")),
            preferences = PreferencesData(language = "hi", lockTimeout = "5m")
        )

        assertEquals(file, SettingsFormat.decode(SettingsFormat.encode(file)))
    }

    @Test
    fun aNewerFormatVersionIsRefused() {
        assertEquals(SettingsFileError.NEWER_VERSION, error("""{"formatVersion":2}"""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val file = SettingsFormat.decode(
            """
            {"formatVersion":1,"futureThing":{"a":1},
             "accounts":[{"name":"Cash","type":"wallet","colour":"blue"}]}
            """.trimIndent().toByteArray()
        )

        assertEquals(listOf(AccountData("Cash", "wallet")), file.accounts)
    }

    @Test
    fun jsonThatIsNotASettingsFileIsDamaged() {
        assertEquals(SettingsFileError.DAMAGED, error("""{"accounts":[]}"""))
        assertEquals(SettingsFileError.DAMAGED, error("not json"))
        assertEquals(SettingsFileError.DAMAGED, error("""{"formatVersion":1,"accounts":5}"""))
    }
}
