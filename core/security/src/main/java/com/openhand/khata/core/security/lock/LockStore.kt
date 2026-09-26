package com.openhand.khata.core.security.lock

import android.content.Context

/** Small key-value storage for lock settings and hashes. Tests use an in-memory map. */
interface LockStore {
    fun getString(key: String): String?

    fun getLong(key: String): Long?

    fun edit(block: LockStoreEditor.() -> Unit)
}

interface LockStoreEditor {
    fun putString(key: String, value: String?)

    fun putLong(key: String, value: Long?)
}

/** App-private SharedPreferences. Android backup is off for the whole app (PRD principle 5). */
class SharedPreferencesLockStore(context: Context) : LockStore {
    private val prefs = context.getSharedPreferences("khata_lock", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getLong(key: String): Long? =
        if (prefs.contains(key)) prefs.getLong(key, 0) else null

    override fun edit(block: LockStoreEditor.() -> Unit) {
        // commit(), not apply(): a PIN change must be on disk before we report success.
        val editor = prefs.edit()
        object : LockStoreEditor {
            override fun putString(key: String, value: String?) {
                if (value == null) editor.remove(key) else editor.putString(key, value)
            }

            override fun putLong(key: String, value: Long?) {
                if (value == null) editor.remove(key) else editor.putLong(key, value)
            }
        }.block()
        check(editor.commit()) { "Couldn't save lock settings" }
    }
}
