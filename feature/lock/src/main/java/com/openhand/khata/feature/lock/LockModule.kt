package com.openhand.khata.feature.lock

import android.content.Context
import android.os.SystemClock
import com.openhand.khata.core.security.lock.AppLockManager
import com.openhand.khata.core.security.lock.LockSettings
import com.openhand.khata.core.security.lock.LockStore
import com.openhand.khata.core.security.lock.PinManager
import com.openhand.khata.core.security.lock.SharedPreferencesLockStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LockModule {
    @Provides
    @Singleton
    fun provideLockStore(@ApplicationContext context: Context): LockStore =
        SharedPreferencesLockStore(context)

    @Provides
    @Singleton
    fun provideLockSettings(store: LockStore) = LockSettings(store)

    @Provides
    @Singleton
    fun providePinManager(store: LockStore) = PinManager(store)

    @Provides
    @Singleton
    fun provideAppLockManager(settings: LockSettings) =
        AppLockManager(settings, SystemClock::elapsedRealtime)
}
