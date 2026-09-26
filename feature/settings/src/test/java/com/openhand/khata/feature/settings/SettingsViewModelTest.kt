package com.openhand.khata.feature.settings

import com.openhand.khata.core.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun exposesTheInstalledVersion() {
        val viewModel = SettingsViewModel(AppInfo(versionName = "1.2.3"))
        assertEquals("1.2.3", viewModel.versionName)
    }
}
