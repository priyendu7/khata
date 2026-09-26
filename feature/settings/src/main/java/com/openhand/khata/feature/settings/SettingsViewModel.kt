package com.openhand.khata.feature.settings

import androidx.lifecycle.ViewModel
import com.openhand.khata.core.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
@Inject
constructor(appInfo: AppInfo) : ViewModel() {
    val versionName: String = appInfo.versionName
}
