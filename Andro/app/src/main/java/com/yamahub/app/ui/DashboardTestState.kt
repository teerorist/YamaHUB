package com.yamahub.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Stany panelu TEST na Dashboard – przeżywają wyjście do Ustawień i powrót.
 */
object DashboardTestState {
    var useSimSpeed by mutableStateOf(false)
    var simSpeed by mutableFloatStateOf(0f)
    var useSimRpm by mutableStateOf(false)
    var simRpm by mutableFloatStateOf(0f)
    var fuelLevel by mutableFloatStateOf(0.5f)
    var ambientBrightness by mutableFloatStateOf(1.0f)

    var actualScreenBrightness by mutableFloatStateOf(0.8f)
    var externalLightIntensity by mutableFloatStateOf(0.5f)
}
