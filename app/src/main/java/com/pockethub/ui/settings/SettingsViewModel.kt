package com.pockethub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Dark)

    val customClientId: StateFlow<String> = settings.customClientId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val customClientSecret: StateFlow<String> = settings.customClientSecret
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setCustomOAuthClient(id: String, secret: String) {
        viewModelScope.launch { settings.setCustomOAuthClient(id, secret) }
    }
}
