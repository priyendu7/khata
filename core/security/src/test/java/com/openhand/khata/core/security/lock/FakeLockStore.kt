package com.openhand.khata.core.security.lock

class FakeLockStore : LockStore {
    val values = mutableMapOf<String, Any>()

    override fun getString(key: String) = values[key] as String?

    override fun getLong(key: String) = values[key] as Long?

    override fun edit(block: LockStoreEditor.() -> Unit) {
        object : LockStoreEditor {
            override fun putString(key: String, value: String?) {
                if (value == null) values.remove(key) else values[key] = value
            }

            override fun putLong(key: String, value: Long?) {
                if (value == null) values.remove(key) else values[key] = value
            }
        }.block()
    }
}
