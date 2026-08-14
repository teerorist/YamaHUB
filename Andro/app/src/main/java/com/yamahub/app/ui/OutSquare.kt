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
fun OutSquare(label: String, level: Float, onColor: Color, isAssigned: Boolean = true) {
    val t = level.coerceIn(0f, 1f)
    val offColor = if (isAssigned) Color(0xFF000000) else Color(0xFF333333)

    val bg = if (t <= 0.01f) {
        offColor
    } else {
        // mieszanie OFF → kolor funkcji proporcjonalnie do level (fade)
        Color(
            red = offColor.red + (onColor.red - offColor.red) * t,
            green = offColor.green + (onColor.green - offColor.green) * t,
            blue = offColor.blue + (onColor.blue - offColor.blue) * t,
            alpha = 1f
        )
    }
    val fg = if (t > 0.35f) {
        if (onColor == COL_WHITE || onColor == COL_CYAN) Color(0xFF111111)
        else Color.White
    } else {
        Color.White.copy(alpha = if (isAssigned) 0.85f else 0.45f)
    }

    Box(
        Modifier
            .size(width = 46.dp, height = 46.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Center
        )
    }
}
