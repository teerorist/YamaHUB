package com.yamahub.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Stany panelu TEST na Dashboard – przeżywają wyjście do Ustawień i powrót.
 */
object DashboardTestState {
    var neutral by mutableStateOf(false)
    var oil by mutableStateOf(false)
    var useSimSpeed by mutableStateOf(false)
    var simSpeed by mutableFloatStateOf(0f)
    var useSimRpm by mutableStateOf(true)
    var simRpm by mutableFloatStateOf(0f)
}
