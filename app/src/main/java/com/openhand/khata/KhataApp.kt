package com.openhand.khata

import android.app.Application
import com.openhand.khata.feature.lock.AppLockLifecycle
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KhataApp : Application() {
    @Inject lateinit var appLockLifecycle: AppLockLifecycle

    override fun onCreate() {
        super.onCreate()
        appLockLifecycle.start()
    }
}
