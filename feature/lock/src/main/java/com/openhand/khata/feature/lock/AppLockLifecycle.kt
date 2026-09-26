package com.openhand.khata.feature.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.openhand.khata.core.security.lock.AppLockManager
import javax.inject.Inject

/** Tells [AppLockManager] when the whole app goes to and returns from the background. */
class AppLockLifecycle @Inject constructor(private val appLock: AppLockManager) :
    DefaultLifecycleObserver {
    /** Call once from `Application.onCreate`. */
    fun start() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    override fun onStart(owner: LifecycleOwner) = appLock.onForeground()

    override fun onStop(owner: LifecycleOwner) = appLock.onBackground()
}
