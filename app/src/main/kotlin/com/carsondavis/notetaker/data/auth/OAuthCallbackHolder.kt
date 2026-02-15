package com.carsondavis.notetaker.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class OAuthCallbackData(
    val code: String,
    val state: String
)

@Singleton
class OAuthCallbackHolder @Inject constructor() {
    private val _callback = MutableStateFlow<OAuthCallbackData?>(null)
    val callback: StateFlow<OAuthCallbackData?> = _callback.asStateFlow()

    fun setCallback(data: OAuthCallbackData) {
        _callback.value = data
    }

    fun clear() {
        _callback.value = null
    }
}
