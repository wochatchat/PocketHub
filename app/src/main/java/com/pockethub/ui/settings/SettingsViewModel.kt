package com.pockethub.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.local.AccountDao
import com.pockethub.data.remote.NotifScheduler
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val accountDao: AccountDao,
    private val notifScheduler: NotifScheduler,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Dark)

    val appLocale: StateFlow<AppLocale> = settings.appLocale
        .map { AppLocale.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLocale.SYSTEM)

    val customClientId: StateFlow<String> = settings.customClientId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val customClientSecret: StateFlow<String> = settings.customClientSecret
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val notifPollMinutes: StateFlow<Int> = settings.notifPollMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val translateTarget: StateFlow<String?> = settings.translateTarget
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _accountCount = MutableStateFlow(0)
    val accountCount: StateFlow<Int> = _accountCount

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes

    init { refreshAccountCount() }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAppLocale(locale: AppLocale) {
        viewModelScope.launch {
            settings.setAppLocale(locale.key)
            val locales = locale.localeTag?.let { LocaleListCompat.create(Locale(it)) }
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun setCustomOAuthClient(id: String, secret: String) {
        viewModelScope.launch { settings.setCustomOAuthClient(id, secret) }
    }

    fun setNotifPollMinutes(minutes: Int) {
        viewModelScope.launch {
            settings.setNotifPollMinutes(minutes)
            notifScheduler.schedule(minutes)
        }
    }

    fun setTranslateTarget(target: String?) {
        viewModelScope.launch { settings.setTranslateTarget(target) }
    }

    fun refreshAccountCount() {
        viewModelScope.launch {
            _accountCount.value = accountDao.allAccounts().first().size
        }
    }

    fun setCacheSize(bytes: Long) {
        _cacheSizeBytes.value = bytes
    }
}
