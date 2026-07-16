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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}
