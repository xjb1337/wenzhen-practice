package com.xjb.wenzhen.ui

import com.xjb.wenzhen.data.update.UpdateInfo
import java.io.File

data class ConnectionTestUiState(
    val isTesting: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean? = null,
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val update: UpdateInfo) : UpdateUiState
    data class Downloading(val update: UpdateInfo, val progress: Int) : UpdateUiState
    data class Downloaded(val update: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(val message: String, val update: UpdateInfo? = null) : UpdateUiState
}
