package com.pockethub.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide channel for session-level events that originate from places which
 * can't directly touch the UI / navigation layer — most importantly the OkHttp
 * [AuthInterceptor], which runs on the network thread and can't suspend.
 *
 * Currently fired events:
 *  - [TokenInvalid]: a request came back 401, the active token is dead/revoked,
 *    and the user should be signed out and returned to login. Consumers (e.g.
 *    AppStartupViewModel) listen and call the normal sign-out flow.
 */
@Singleton
class SessionEventBus @Inject constructor() {
    sealed interface Event {
        data object TokenInvalid : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun emit(event: Event) {
        // tryEmit is non-suspending, safe from a synchronous interceptor chain.
        _events.tryEmit(event)
    }
}
