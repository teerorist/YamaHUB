package com.yamahub.app.ui

import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yamahub.app.BleHub
import com.yamahub.app.HubNotification
import com.yamahub.app.InputCfgItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import com.yamahub.app.R
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.translate
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.mutableLongStateOf

@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val bkgBitmap = ImageBitmap.imageResource(id = R.drawable.bkg)
    val bkgBrush = remember(bkgBitmap) {
        ShaderBrush(ImageShader(bkgBitmap, TileMode.Repeated, TileMode.Repeated))
    }
    
    val activity = context as ComponentActivity
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()

    var speedKmh by remember { mutableFloatStateOf(0f) }
    var rpm by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    var cfg by remember { mutableStateOf<List<InputCfgItem>>(emptyList()) }
    var rows by remember { mutableStateOf<List<ControlInRow>>(emptyList()) }

    var states by remember { mutableStateOf(List(10) { false }) }
    var leftOut by remember { mutableIntStateOf(1) }
    var rightOut by remember { mutableIntStateOf(5) }
    var neutralOut by remember { mutableIntStateOf(0) }
    var oilOut by remember { mutableIntStateOf(0) }
    var lowBeamOut by remember { mutableIntStateOf(0) }
    var hiBeamOut by remember { mutableIntStateOf(0) }
    var fadeSpeed by remember { mutableIntStateOf(12) }
    var fadeCurve by remember { mutableIntStateOf(1) }
    var acSpeedThreshold by remember { mutableIntStateOf(20) }

    val leftActive = states.getOrElse(leftOut - 1) { false }
    val rightActive = states.getOrElse(rightOut - 1) { false }
    val hazard = leftActive && rightActive && leftOut != rightOut
    val hazardOn = hazard
    val leftLevel = rememberBlinkLevel(leftActive || hazard, fadeSpeed, fadeCurve)
    val rightLevel = rememberBlinkLevel(rightActive || hazard, fadeSpeed, fadeCurve)

    val neutralOn = if (neutralOut in 1..10) states.getOrElse(neutralOut - 1) { false } else DashboardTestState.neutral
    val oilOn = if (oilOut in 1..10) states.getOrElse(oilOut - 1) { false } else DashboardTestState.oil
    val hiBeamOn = if (hiBeamOut in 1..10) states.getOrElse(hiBeamOut - 1) { false } else false
    val lowBeamOn = if (lowBeamOut in 1..10) states.getOrElse(lowBeamOut - 1) { false } else false

    val displaySpeed = if (DashboardTestState.useSimSpeed) DashboardTestState.simSpeed else speedKmh
    val displayRpm = if (DashboardTestState.useSimRpm) DashboardTestState.simRpm.toInt() else rpm

    fun applyCfg(list: List<InputCfgItem>) {
        cfg = list
        rows = buildRows(list)
        leftOut = list.firstOrNull { it.mode == 2 }?.outNum?.coerceIn(1, 10) ?: 1
        rightOut = list.firstOrNull { it.mode == 3 }?.outNum?.coerceIn(1, 10) ?: 5
        val neutral = list.firstOrNull {
            it.mode == 1 && (it.name.lowercase().contains("neutral") || it.name.lowercase().contains("luz"))
        }
        neutralOut = neutral?.outNum?.coerceIn(1, 10) ?: 0
        val oil = list.firstOrNull {
            it.mode == 1 && (it.name.lowercase().contains("oil") || it.name.lowercase().contains("olej"))
        }
        oilOut = oil?.outNum?.coerceIn(1, 10) ?: 0
        val lights = list.filter {
            val n = it.name.lowercase()
            n.contains("lights") || n.contains("beam") || n.contains("light")
        }.sortedBy { it.inNum }
        if (lights.size >= 2) {
            lowBeamOut = lights[0].outNum.coerceIn(1, 10)
            hiBeamOut = lights[1].outNum.coerceIn(1, 10)
        } else {
            lowBeamOut = lights.firstOrNull()?.outNum?.coerceIn(1, 10) ?: 0
            hiBeamOut = lights.firstOrNull()?.let { parseLightsHi(it.name) } ?: 0
        }
    }

    DisposableEffect(Unit) {
        val prevConn = ble.onConnectionChanged
        val prevState = ble.onStateReceived
        val prevCfg = ble.onInputCfg
        val prevBlink = ble.onConfigReceived
        val prevRaw = ble.onRawMessage
        ble.onConnectionChanged = { c -> isConnected = c; HubNotification.update(context, c); prevConn?.invoke(c) }
        ble.onStateReceived = { list -> 
            if (list.size >= 10) {
                val next = list.take(10)
                if (next != states) {
                    states = next
                }
            }
            prevState?.invoke(list) 
        }
        ble.onInputCfg = { list -> if (list.size in 9..10) applyCfg(list); prevCfg?.invoke(list) }
        ble.onConfigReceived = { fade, _, curve, ac -> 
            fadeSpeed = fade.coerceIn(4, 60)
            fadeCurve = curve.coerceIn(0, 2)
            acSpeedThreshold = ac.coerceIn(5, 100)
            prevBlink?.invoke(fade, 0, curve, ac) 
        }
        ble.onRawMessage = { msg -> if (msg.startsWith("RPM:") && !DashboardTestState.useSimRpm) rpm = msg.removePrefix("RPM:").trim().toIntOrNull() ?: 0; prevRaw?.invoke(msg) }
        if (ble.isConnected) { ble.requestState(); ble.requestInputCfg(); ble.sendCommand("GET_CFG") }
        onDispose { ble.onConnectionChanged = prevConn; ble.onStateReceived = prevState; ble.onInputCfg = prevCfg; ble.onConfigReceived = prevBlink; ble.onRawMessage = prevRaw }
    }

    LaunchedEffect(isConnected) { while (isConnected) { ble.requestState(); delay(250) } }

    // Auto LOW BEAM logic
    LaunchedEffect(displaySpeed) {
        if (displaySpeed > acSpeedThreshold && lowBeamOut in 1..10 && !lowBeamOn && isConnected) {
            ble.setOutput(lowBeamOut, true)
        }
    }

    DisposableEffect(isConnected, DashboardTestState.useSimSpeed) {
        if (!isConnected || DashboardTestState.useSimSpeed) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { speedKmh = location.speed * 3.6f; ble.sendSpeed(speedKmh) }
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener) } catch (_: SecurityException) {}
        onDispose { try { lm.removeUpdates(listener) } catch (_: Exception) {} }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).background(bkgBrush, alpha = DashboardTestState.ambientBrightness)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showShutdownDialog = true }, enabled = isConnected) {
                    Icon(Icons.Default.PowerSettingsNew, "Wyłącz HUB", tint = Color(0xFFF44336))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Ustawienia") }
            }

            Box(Modifier.weight(1f).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                SmithsGauge(
                    rpm = displayRpm.toFloat(),
                    speed = displaySpeed,
                    leftTurnLevel = leftLevel,
                    rightTurnLevel = rightLevel,
                    lowBeamOn = lowBeamOn,
                    hiBeamOn = hiBeamOn,
                    neutralOn = neutralOn,
                    oilOn = oilOn,
                    fuelLevel = DashboardTestState.fuelLevel,
                    ambientBrightness = DashboardTestState.ambientBrightness
                )
            }

            Spacer(Modifier.height(12.dp))

            // Starter Button
            Box(
                modifier = Modifier.size(100.dp).pointerInput(isConnected) {
                    detectTapGestures(onPress = {
                        if (!isConnected || !DashboardTestState.neutral) return@detectTapGestures
                        pressed = true; ble.sendCommand("IN10:1")
                        try { awaitRelease() } finally {
                            pressed = false
                            scope.launch { delay(40); ble.sendCommand("IN10:0"); delay(40); ble.sendCommand("IN10:0") }
                        }
                    })
                },
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = CircleShape, color = when { pressed -> MaterialTheme.colorScheme.primary; !DashboardTestState.neutral -> MaterialTheme.colorScheme.surfaceVariant; else -> MaterialTheme.colorScheme.secondaryContainer }, modifier = Modifier.fillMaxSize()) {}
                Text(when { pressed -> "ON"; !DashboardTestState.neutral -> "N?"; else -> "START" }, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Test Panel
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
                val tiny = MaterialTheme.typography.labelSmall
                Column(Modifier.padding(6.dp)) {
                    // Row 1: Sliders
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        // Speed
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PRĘDK: ${DashboardTestState.simSpeed.toInt()}", style = tiny)
                            Slider(value = DashboardTestState.simSpeed, onValueChange = { DashboardTestState.simSpeed = it; speedKmh = it; if (isConnected) ble.sendSpeed(it) }, valueRange = 0f..200f)
                        }
                        Spacer(Modifier.width(8.dp))
                        // RPM
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RPM: ${DashboardTestState.simRpm.toInt()}", style = tiny)
                            Slider(value = DashboardTestState.simRpm, onValueChange = { DashboardTestState.simRpm = it }, valueRange = 0f..12000f)
                        }
                        Spacer(Modifier.width(8.dp))
                        // Fuel
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PALIWO: ${(DashboardTestState.fuelLevel * 100).toInt()}%", style = tiny)
                            Slider(value = DashboardTestState.fuelLevel, onValueChange = { DashboardTestState.fuelLevel = it }, valueRange = 0f..1f)
                        }
                    }
                    
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    
                    // Row 2: Oil & Neutral
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("OLEJ", style = tiny)
                            Switch(checked = oilOn, onCheckedChange = { DashboardTestState.oil = it; if (oilOut in 1..10) ble.setOutput(oilOut, it) }, modifier = Modifier.scale(0.7f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NEUTRAL", style = tiny)
                            Switch(checked = neutralOn, onCheckedChange = { DashboardTestState.neutral = it; if (neutralOut in 1..10) ble.setOutput(neutralOut, it) }, modifier = Modifier.scale(0.7f))
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Row 3: Beams & Turns
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                        val rowL = rows.find { it.mode == 2 }
                        val rowP = rows.find { it.mode == 3 }
                        val rowLow = rows.find { it.title == "LIGHTS" && (it.subtitle?.contains("LOW", true) == true || it.subtitle == null) }
                        val rowHi = rows.find { it.title == "LIGHTS" && it.subtitle?.contains("HI", true) == true }

                        val outLevelLambda: (Int) -> Float = { if (states.getOrElse(it - 1) { false }) 1f else 0f }

                        DashboardTestButton("L", rowL, ble, leftActive, rightActive, hazardOn, scope, outLevelLambda)
                        DashboardTestButton("LOW", rowLow, ble, leftActive, rightActive, hazardOn, scope, outLevelLambda)
                        DashboardTestButton("HI", rowHi, ble, leftActive, rightActive, hazardOn, scope, outLevelLambda)
                        DashboardTestButton("P", rowP, ble, leftActive, rightActive, hazardOn, scope, outLevelLambda)
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Row 4: Brightness & Info
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SYM. JASNOŚĆ: ${(DashboardTestState.ambientBrightness * 100).toInt()}%", style = tiny)
                            Slider(value = DashboardTestState.ambientBrightness, onValueChange = { DashboardTestState.ambientBrightness = it }, valueRange = 0f..1f)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ekran: ${(DashboardTestState.actualScreenBrightness * 100).toInt()}%", style = tiny)
                            Text("Zewn: ${(DashboardTestState.externalLightIntensity * 100).toInt()}%", style = tiny)
                        }
                    }
                }
            }
        }

        if (showShutdownDialog) {
            AlertDialog(
                onDismissRequest = { showShutdownDialog = false },
                title = { Text("Wyłączyć HUB?") },
                text = { Text("Aplikacja zostanie zamknięta.") },
                confirmButton = { TextButton(onClick = {
                    showShutdownDialog = false; if (isConnected) ble.sendCommand("SHUTDOWN_NOW")
                    HubNotification.cancel(context); Handler(Looper.getMainLooper()).postDelayed({ activity.finishAffinity(); Process.killProcess(Process.myPid()) }, 400)
                }) { Text("Wyłącz", color = Color(0xFFF44336)) } },
                dismissButton = { TextButton(onClick = { showShutdownDialog = false }) { Text("Anuluj") } }
            )
        }
    }
}

@Composable
fun SmithsGauge(rpm: Float, speed: Float, leftTurnLevel: Float, rightTurnLevel: Float, lowBeamOn: Boolean, hiBeamOn: Boolean, neutralOn: Boolean, oilOn: Boolean, fuelLevel: Float, ambientBrightness: Float) {
    val bgOff = painterResource(id = R.drawable.tachometer_0)
    val bgOn = painterResource(id = R.drawable.tachometer_1)
    val fuelBgOff = painterResource(id = R.drawable.fuel_level_0)
    val fuelBgOn = painterResource(id = R.drawable.fuel_level_1)
    
    // Explicit Animatable for the most reliable smooth transition
    val backlightAlpha = remember { Animatable(if (lowBeamOn) 1f else 0f) }
    
    LaunchedEffect(lowBeamOn) {
        backlightAlpha.animateTo(
            targetValue = if (lowBeamOn) 1f else 0f,
            animationSpec = tween(durationMillis = 1000)
        )
    }
    
    // Load icons
    val left0 = painterResource(id = R.drawable.left_0)
    val left1 = painterResource(id = R.drawable.left_1)
    val right0 = painterResource(id = R.drawable.right_0)
    val right1 = painterResource(id = R.drawable.right_1)
    val hi0 = painterResource(id = R.drawable.hi_0)
    val hi1 = painterResource(id = R.drawable.hi_1)
    val low0 = painterResource(id = R.drawable.low_0)
    val low1 = painterResource(id = R.drawable.low_1)
    val oil0 = painterResource(id = R.drawable.oil_0)
    val oil1 = painterResource(id = R.drawable.oil_1)
    
    // Neutral and Fuel
    val neutral0 = painterResource(id = R.drawable.n_0)
    val neutral1 = painterResource(id = R.drawable.n_1)
    val fuel0 = painterResource(id = R.drawable.fuel_0)
    val fuel1 = painterResource(id = R.drawable.fuel_1)
    
    // Needle and Cap
    val needlePainter = painterResource(id = R.drawable.needle)
    val needleBkg0 = painterResource(id = R.drawable.needle_bkg_0)
    val needleBkg1 = painterResource(id = R.drawable.needle_bkg_1)
    val capPainter = painterResource(id = R.drawable.cap)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 1. Layer: Fuel Backgrounds (Bottom-most)
        Image(
            painter = fuelBgOff,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alpha = ambientBrightness
        )
        Image(
            painter = fuelBgOn,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backlightAlpha.value }
        )

        // 2. Layer: Static Background (Off)
        Image(
            painter = bgOff,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alpha = ambientBrightness
        )
        
        // 3. Layer: Backlit Background (On) - Smooth fade controlled by Animatable
        Image(
            painter = bgOn,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backlightAlpha.value }
        )

        // 4. Layer: Dynamic Elements (Needles, Icons, LCD)
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Set pivot point to exact center of the component
            val center = Offset(size.width / 2f, size.height / 2f)

            // RPM Scale constants
            val startAngle = 145f 
            val sweepAngle = 250f

            // 2. Speed Display (LCD Window)
            val sp = Paint().apply { 
                color = Color(0xFFC5D1C5).toArgb() 
                textSize = 34.sp.toPx()
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create("monospace", Typeface.NORMAL)
            }
            drawIntoCanvas { 
                it.nativeCanvas.drawText(speed.toInt().toString(), center.x + 27.dp.toPx(), center.y + 55.dp.toPx(), sp)
            }

            // 3. Icons
            val iconSize = size 
            with(if (leftTurnLevel > 0.5f) left1 else left0) { draw(size = iconSize, alpha = if (leftTurnLevel > 0.5f) 1f else ambientBrightness) }
            with(if (rightTurnLevel > 0.5f) right1 else right0) { draw(size = iconSize, alpha = if (rightTurnLevel > 0.5f) 1f else ambientBrightness) }
            with(if (hiBeamOn) hi1 else hi0) { draw(size = iconSize, alpha = if (hiBeamOn) 1f else ambientBrightness) }
            with(if (lowBeamOn) low1 else low0) { draw(size = iconSize, alpha = if (lowBeamOn) 1f else ambientBrightness) }
            with(if (oilOn) oil1 else oil0) { draw(size = iconSize, alpha = if (oilOn) 1f else ambientBrightness) }
            with(if (neutralOn) neutral1 else neutral0) { draw(size = iconSize, alpha = if (neutralOn) 1f else ambientBrightness) }
            with(if (fuelLevel < 0.15f) fuel1 else fuel0) { draw(size = iconSize, alpha = if (fuelLevel < 0.15f) 1f else ambientBrightness) }

            // 4. Fuel Gauge Needle
            drawFuelSubGauge(center.x, center.y + 112.dp.toPx(), fuelLevel)

            // 5. RPM Needle
            val constrainedRpm = rpm.coerceIn(0f, 12000f)
            val needleAngle = startAngle + (constrainedRpm / 12000f) * sweepAngle
            
            val rotationDegrees = needleAngle - 270f

            // Shadow: Always 20px vertically below the needle, pivot on vertical axis
            translate(0f, 20f) {
                rotate(rotationDegrees, center) {
                    with(needleBkg1) {
                        draw(size = size, alpha = 0.2f * ambientBrightness, colorFilter = ColorFilter.tint(Color.Black))
                    }
                    with(needlePainter) {
                        draw(size = size, alpha = 0.3f * ambientBrightness, colorFilter = ColorFilter.tint(Color.Black))
                    }
                }
            }
            
            // Needle Background - Ambient (Off)
            rotate(rotationDegrees, center) {
                with(needleBkg0) {
                    draw(size = size, alpha = 1.0f)
                }
            }
            
            // Needle Background - Backlit (On)
            rotate(rotationDegrees, center) {
                with(needleBkg1) {
                    draw(size = size, alpha = backlightAlpha.value)
                }
            }
            
            // Needle: Drawn on top of shadow and background
            rotate(rotationDegrees, center) {
                with(needlePainter) {
                    draw(size = size, alpha = ambientBrightness)
                }
            }
            
            // 6. Center Cap
    //        with(capPainter) {
    //            draw(size = size)
     //       }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFuelSubGauge(x: Float, y: Float, level: Float) {
    val needleLen = 18.dp.toPx()
    // Align with the "E" and "F" on the image
    val rotation = -150f + level.coerceIn(0f, 1f) * 120f
    
    rotate(rotation, Offset(x, y)) {
        drawLine(
            color = Color.White,
            start = Offset(x, y),
            end = Offset(x, y - needleLen),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun DashboardTestButton(
    label: String,
    row: ControlInRow?,
    ble: com.yamahub.app.BleManager,
    leftActive: Boolean,
    rightActive: Boolean,
    hazardOn: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    outLevel: (Int) -> Float
) {
    var pressed by remember { mutableStateOf(false) }
    var downAt by remember { mutableLongStateOf(0L) }
    
    // Fix stale closures by wrapping dynamic states
    val currentBle by rememberUpdatedState(ble)
    val currentRow by rememberUpdatedState(row)
    val currentLeftActive by rememberUpdatedState(leftActive)
    val currentRightActive by rememberUpdatedState(rightActive)
    val currentHazardOn by rememberUpdatedState(hazardOn)
    val currentOutLevel by rememberUpdatedState(outLevel)

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(width = 44.dp, height = 28.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val rowRef = currentRow ?: return@detectTapGestures
                        pressed = true
                        downAt = System.currentTimeMillis()
                        ControlActions.onDown(currentBle, rowRef, currentLeftActive, currentRightActive, currentHazardOn, scope)
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                            val held = System.currentTimeMillis() - downAt
                            if (label == "HI" && rowRef.title == "LIGHTS") {
                                ControlActions.onUp(currentBle, rowRef, 1000L, currentLeftActive, currentRightActive, currentHazardOn, currentOutLevel)
                            } else if (label == "LOW" && rowRef.title == "LIGHTS") {
                                ControlActions.onUp(currentBle, rowRef, 100L, currentLeftActive, currentRightActive, currentHazardOn, currentOutLevel)
                            } else {
                                ControlActions.onUp(currentBle, rowRef, held, currentLeftActive, currentRightActive, currentHazardOn, currentOutLevel)
                            }
                        }
                    }
                )
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
