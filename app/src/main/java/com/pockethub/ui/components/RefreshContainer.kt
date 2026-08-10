package com.pockethub.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

/**
 * Unified pull-to-refresh wrapper used across the app so every refreshable
 * surface shares the same look, feel and loading semantics.
 *
 * Wraps [PullToRefreshBox] and keeps the indicator visible until the caller
 * clears [isRefreshing]. This is important because many screens here do network
 * loads via ViewModel flows that are *not* guarded by a boolean — the indicator
 * is the only way the user knows the pull action was accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable () -> Unit,
) {
    // Auto-clear the indicator once refreshing stops, so a lingering true doesn't
    // leave a stuck spinner after the owning screen rotates or re-composes.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}
