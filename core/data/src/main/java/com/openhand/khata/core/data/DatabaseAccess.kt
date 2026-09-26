package com.openhand.khata.core.data

import com.openhand.khata.core.database.KhataDatabase
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

// The database is opened (Keystore + disk) the first time it's used, so every access goes through
// these helpers, which run on the IO dispatcher and never on the main thread.

internal fun <T> Lazy<KhataDatabase>.observe(query: (KhataDatabase) -> Flow<T>): Flow<T> =
    flow { emitAll(query(get())) }.flowOn(Dispatchers.IO)

internal suspend fun <T> Lazy<KhataDatabase>.io(block: suspend (KhataDatabase) -> T): T =
    withContext(Dispatchers.IO) { block(get()) }
