package com.focuslock.app.enforcement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BlockerRuntimeState(
    val connected: Boolean = false,
    val currentFeed: FeedKind? = null,
    val lastDetectedFeed: FeedKind? = null,
    val error: String? = null,
)

/** Connection health only; no raw screen content or usage history is retained here. */
object BlockerRuntime {
    private val mutableState = MutableStateFlow(BlockerRuntimeState())
    val state = mutableState.asStateFlow()

    fun connected() { mutableState.value = mutableState.value.copy(connected = true, error = null) }
    fun disconnected() { mutableState.value = mutableState.value.copy(connected = false, currentFeed = null) }
    fun detected(feed: FeedKind?) {
        mutableState.value = mutableState.value.copy(
            currentFeed = feed,
            lastDetectedFeed = feed ?: mutableState.value.lastDetectedFeed,
            error = null,
        )
    }
    fun error(message: String) { mutableState.value = mutableState.value.copy(error = message) }
}
