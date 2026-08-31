package com.pockethub.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.local.AccountDao
import com.pockethub.data.remote.NotifScheduler
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.data.reporting.IssueKind
import com.pockethub.data.reporting.IssueReporter
import com.pockethub.data.reporting.IssueReportScheduler
import com.pockethub.ui.theme.AppStyle
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
    private val issueReporter: IssueReporter,
    private val issueReportScheduler: IssueReportScheduler,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Dark)

    val appStyle: StateFlow<AppStyle?> = settings.appStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val followSystemTheme: StateFlow<Boolean> = settings.followSystemTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setFollowSystemTheme(on: Boolean) {
        viewModelScope.launch { settings.setFollowSystemTheme(on) }
    }

    val appLocale: StateFlow<AppLocale> = settings.appLocale
        .map { AppLocale.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLocale.SYSTEM)

    val customClientId: StateFlow<String> = settings.customClientId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val customClientSecret: StateFlow<String> = settings.customClientSecret
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val oauthBackendUrl: StateFlow<String> = settings.oauthBackendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_OAUTH_BACKEND_URL)
    val dohUrl: StateFlow<String> = settings.dohUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_DOH_URL)

    /** GitHub file-download accelerator prefix (net branch experiment). */
    val downloadMirrorPrefix: StateFlow<String> = settings.downloadMirrorPrefix
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val notifPollMinutes: StateFlow<Int> = settings.notifPollMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val translateTarget: StateFlow<String?> = settings.translateTarget
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Severe-issue reporting
    val issueReportEnabled: StateFlow<Boolean> = settings.issueReportEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val issueReportIntervalDays: StateFlow<Int> = settings.issueReportIntervalDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, 7)

    val issueReportEmail: StateFlow<String> = settings.issueReportEmail
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val issueReportMode: StateFlow<String> = settings.issueReportMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "email")

    val issueReportTargetRepo: StateFlow<String> = settings.issueReportTargetRepo
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _issueCount = MutableStateFlow(0)
    val issueCount: StateFlow<Int> = _issueCount

    private val _accountCount = MutableStateFlow(0)
    val accountCount: StateFlow<Int> = _accountCount

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes

    init {
        refreshAccountCount()
        refreshIssueCount()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAppStyle(style: AppStyle?) {
        viewModelScope.launch { settings.setAppStyle(style) }
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

    fun setOAuthBackendUrl(url: String) {
        viewModelScope.launch { settings.setOAuthBackendUrl(url) }
    }

    fun setDohUrl(url: String) {
        viewModelScope.launch { settings.setDohUrl(url) }
    }
    fun setDownloadMirrorPrefix(prefix: String) {
        viewModelScope.launch { settings.setDownloadMirrorPrefix(prefix) }
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

    /**
     * Snapshot the physical count of issues in the ring buffer — used by the
     * Settings screen to say "currently N severe issues collected".
     */
    fun refreshIssueCount() {
        viewModelScope.launch { _issueCount.value = issueReporter.readLog().size }
    }

    /** One-shot read of the local severe-event log for the email report. */
    suspend fun issueEvents(): List<com.pockethub.data.reporting.IssueEvent> = issueReporter.readLog()

    fun setIssueReportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setIssueReportEnabled(enabled)
            issueReportScheduler.rescheduleFromSettings()
        }
    }

    fun setIssueReportIntervalDays(days: Int) {
        viewModelScope.launch {
            settings.setIssueReportIntervalDays(days)
            issueReportScheduler.rescheduleFromSettings()
        }
    }

    fun setIssueReportEmail(email: String) {
        viewModelScope.launch { settings.setIssueReportEmail(email) }
    }

    fun setIssueReportMode(mode: String) {
        viewModelScope.launch {
            settings.setIssueReportMode(mode)
            issueReportScheduler.rescheduleFromSettings()
        }
    }

    fun setIssueReportTargetRepo(slug: String) {
        viewModelScope.launch { settings.setIssueReportTargetRepo(slug) }
    }

    /**
     * Inject a synthetic test issue into the local ring buffer so the user
     * can verify "Open staged mail" → see the email composer pre-filled end to end.
     */
    fun stageTestIssue(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            issueReporter.report(
                kind = IssueKind.ERROR,
                subject = "[测试] 这是一条植入的严重问题样本，验证邮件上报链路是否畅通",
                stackTrace = "TestIssue at com.pockethub.ui.settings.SettingsViewModel:stageTestIssue\n" +
                    "  at com.pockethub.data.reporting.IssueReporter.report(IssueReporter.kt)\n" +
                    "  Triggered by user from Settings → Severe-issue reporting",
                extra = listOf("source" to "manual_test").associate { it },
            )
            refreshIssueCount()
            callback(true)
        }
    }

    /** Forget all local events (used after the user manually emails them out). */
    fun clearIssueLog() {
        viewModelScope.launch {
            issueReporter.clearLog()
            refreshIssueCount()
        }
    }
}
