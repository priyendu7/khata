package com.openhand.khata.di

import com.openhand.khata.BuildConfig
import com.openhand.khata.core.model.AppInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** App-wide bindings that only :app can provide (it owns BuildConfig). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppInfo(): AppInfo = AppInfo(versionName = BuildConfig.VERSION_NAME)
}
