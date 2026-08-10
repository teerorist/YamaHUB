package com.yamahub.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import com.yamahub.app.Prefs
import com.yamahub.app.displayName
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
@Composable
fun HazardRow(
    enabled: Boolean,
    leftLevel: Float,
    rightLevel: Float,
    leftOut: Int,
    rightOut: Int,
    onToggle: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val cardBg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        label = "hazardCard"
    )

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(46.dp)
                .background(cardBg, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp)
                )
                .pointerInput(enabled) {
                    detectTapGestures(
                        onPress = {
                            if (!enabled) return@detectTapGestures
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                            onToggle()
                        }
                    )
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "AWARYJNE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(8.dp))
        OutSquare(
            label = "OUT_%02d".format(leftOut),
            level = leftLevel,
            onColor = COL_ORANGE
        )
        Spacer(Modifier.width(6.dp))
        OutSquare(
            label = "OUT_%02d".format(rightOut),
            level = rightLevel,
            onColor = COL_ORANGE
        )
    }
}

