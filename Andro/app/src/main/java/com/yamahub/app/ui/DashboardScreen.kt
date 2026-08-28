package com.yamahub.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.WindowManager
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
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
    val activity = context as ComponentActivity
    
    // Sensor and Brightness Logic
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        
        val lightListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val lux = event?.values?.get(0) ?: 0f
                // Logarithmic or simple scale? Let's use simple % for now, 
                // but ensure it updates the state correctly.
                DashboardTestState.externalLightIntensity = (lux / 500f).coerceIn(0f, 1f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        
        // Observer for system brightness changes
        val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                try {
                    val sys = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    DashboardTestState.actualScreenBrightness = sys / 255f
                } catch (_: Exception) {}
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            brightnessObserver
        )
        
        // Initial values
        try {
            val sys = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            DashboardTestState.actualScreenBrightness = sys / 255f
        } catch (_: Exception) {}

        onDispose {
            sensorManager.unregisterListener(lightListener)
            context.contentResolver.unregisterContentObserver(brightnessObserver)
        }
    }

    val bkgBitmap = ImageBitmap.imageResource(id = R.drawable.bkg)
    val bkgBrush = remember(bkgBitmap) {
        ShaderBrush(ImageShader(bkgBitmap, TileMode.Repeated, TileMode.Repeated))
    }
    
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()

    var speedKmh by remember { mutableFloatStateOf(0f) }
    var lastDisplaySpeed by remember { mutableFloatStateOf(0f) }
    var rpm by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(ble.isConnected) }
    var starterEnabled by remember { mutableStateOf(ble.starterEnabled) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    var cfg by remember { mutableStateOf<List<InputCfgItem>>(emptyList()) }
    var rows by remember { mutableStateOf<List<ControlInRow>>(emptyList()) }

    var states by remember { mutableStateOf(List(10) { false }) }
    var inputStates by remember { mutableStateOf(List(10) { false }) }
    var leftOut by remember { mutableIntStateOf(1) }
    var rightOut by remember { mutableIntStateOf(5) }
    var neutralOut by remember { mutableIntStateOf(0) }
    var neutralInNum by remember { mutableIntStateOf(0) }
    var oilInNum by remember { mutableIntStateOf(0) }
    var starterInNum by remember { mutableIntStateOf(0) }
    var oilOut by remember { mutableIntStateOf(0) }
    var lowBeamOut by remember { mutableIntStateOf(0) }
    var hiBeamOut by remember { mutableIntStateOf(0) }
    var fadeSpeed by remember { mutableIntStateOf(12) }
    var fadeCurve by remember { mutableIntStateOf(1) }
    var acSpeedThreshold by remember { mutableIntStateOf(20) }
    var autoLights by remember { mutableStateOf(false) }

    val leftActive = states.getOrElse(leftOut - 1) { false }
    val rightActive = states.getOrElse(rightOut - 1) { false }
    val hazard = leftActive && rightActive && leftOut != rightOut
    val hazardOn = hazard
    val blink = rememberBlinkPair(leftActive, rightActive, hazardOn, fadeSpeed, fadeCurve)
    val leftLevel = blink.left
    val rightLevel = blink.right

    val neutralOn = if (neutralInNum in 1..10) inputStates.getOrElse(neutralInNum - 1) { false }
        else if (neutralOut in 1..10) states.getOrElse(neutralOut - 1) { false }
        else DashboardTestState.neutral
    val oilOn = if (oilInNum in 1..10) inputStates.getOrElse(oilInNum - 1) { false }
        else if (oilOut in 1..10) states.getOrElse(oilOut - 1) { false }
        else DashboardTestState.oil
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
            (it.mode == 1 || it.mode == 4) &&
                (it.name.lowercase().contains("neutral") || it.name.lowercase().contains("luz"))
        }
        val neutralSensor = list.firstOrNull {
            it.mode == 4 && (it.name.lowercase().contains("neutral") || it.name.lowercase().contains("luz"))
        }
        neutralInNum = (neutralSensor ?: neutral)?.inNum ?: 0
        val oilSensor = list.firstOrNull {
            it.mode == 4 && (it.name.lowercase().contains("oil") || it.name.lowercase().contains("olej"))
        }
        oilInNum = oilSensor?.inNum ?: 0
        starterInNum = list.firstOrNull { it.mode == 6 }?.inNum ?: 0
        neutralOut = neutral?.outNum?.coerceIn(1, 10) ?: 0
        val oil = list.firstOrNull {
            it.mode == 1 && (it.name.lowercase().contains("oil") || it.name.lowercase().contains("olej"))
        }
        oilOut = oil?.outNum?.coerceIn(1, 10) ?: 0
        val (low, hi) = lightsOutsFromCfg(list)
        lowBeamOut = low
        hiBeamOut = hi
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
        val prevInputStates = ble.onInputStates
        ble.onInputStates = { list -> inputStates = list; prevInputStates?.invoke(list) }
        ble.onInputCfg = { list -> if (list.size in 9..10) applyCfg(list); prevCfg?.invoke(list) }
        val prevStarter = ble.onStarterEnabled
        ble.onStarterEnabled = { enabled -> starterEnabled = enabled; prevStarter?.invoke(enabled) }
        ble.onConfigReceived = { fade, blinks, curve, ac, acOn, lightsOn ->
            fadeSpeed = fade.coerceIn(4, 60)
            fadeCurve = curve.coerceIn(0, 2)
            acSpeedThreshold = ac.coerceIn(5, 30)
            if (lightsOn != null) autoLights = lightsOn
            prevBlink?.invoke(fade, blinks, curve, ac, acOn, lightsOn)
        }
        ble.onRawMessage = { msg -> if (msg.startsWith("RPM:") && !DashboardTestState.useSimRpm) rpm = msg.removePrefix("RPM:").trim().toIntOrNull() ?: 0; prevRaw?.invoke(msg) }
        if (ble.isConnected) { ble.requestState(); ble.requestInputCfg(); ble.sendCommand("GET_CFG") }
        onDispose { ble.onConnectionChanged = prevConn; ble.onStateReceived = prevState; ble.onInputCfg = prevCfg; ble.onConfigReceived = prevBlink; ble.onRawMessage = prevRaw; ble.onStarterEnabled = prevStarter; ble.onInputStates = prevInputStates }
    }

    // Auto lights and blinker cancellation logic
    LaunchedEffect(displaySpeed, autoLights, acSpeedThreshold) {
        if (isConnected) {
            if (autoLights && displaySpeed >= acSpeedThreshold &&
                lowBeamOut in 1..10 && !lowBeamOn
            ) {
                ble.setOutput(lowBeamOut, true)
            }
        }
        lastDisplaySpeed = displaySpeed
    }

    DisposableEffect(isConnected, DashboardTestState.useSimSpeed) {
        if (!isConnected || DashboardTestState.useSimSpeed) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                speedKmh = location.speed * 3.6f
            }
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 0f, listener)
        } catch (_: SecurityException) { }
        onDispose {
            try { lm.removeUpdates(listener) } catch (_: SecurityException) { }
        }
    }

    val configuration = LocalConfiguration.current
    val gaugeWidth = configuration.screenWidthDp.dp * 1.1f

    Box(Modifier.fillMaxSize().background(Color.Black).background(bkgBrush, alpha = DashboardTestState.actualScreenBrightness)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showShutdownDialog = true }, enabled = isConnected) {
                    Icon(Icons.Default.PowerSettingsNew, "Wyłącz HUB", tint = Color(0xFFF44336))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Ustawienia") }
            }

            Box(Modifier.weight(1f).requiredWidth(gaugeWidth).aspectRatio(1f), contentAlignment = Alignment.Center) {
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
                    ambientBrightness = DashboardTestState.actualScreenBrightness
                )
            }

            Spacer(Modifier.height(12.dp))

            // Starter Button
            Box(
                modifier = Modifier.size(100.dp).pointerInput(isConnected) {
                    detectTapGestures(onPress = {
                        if (!isConnected || !starterEnabled || starterInNum !in 1..10)
                            return@detectTapGestures
                        pressed = true; ble.sendCommand("IN:$starterInNum:1")
                        try { awaitRelease() } finally {
                            pressed = false
                            scope.launch {
                                delay(40); ble.sendCommand("IN:$starterInNum:0")
                                delay(40); ble.sendCommand("IN:$starterInNum:0")
                            }
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
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Left Side: 4 vertical columns (50% width)
                        Row(Modifier.weight(1f).fillMaxHeight(), Arrangement.SpaceEvenly) {
                            // 1. Speed
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("SPD", style = tiny)
                                VerticalSlider(value = DashboardTestState.simSpeed, onValueChange = { DashboardTestState.simSpeed = it; speedKmh = it; if (isConnected) ble.sendSpeed(it) }, valueRange = 0f..200f, modifier = Modifier.weight(1f).width(20.dp))
                                Text("${DashboardTestState.simSpeed.toInt()}", style = tiny)
                            }
                            // 2. RPM
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("RPM", style = tiny)
                                VerticalSlider(value = DashboardTestState.simRpm, onValueChange = { DashboardTestState.simRpm = it }, valueRange = 0f..12000f, modifier = Modifier.weight(1f).width(20.dp))
                                Text("${(DashboardTestState.simRpm/1000).toInt()}k", style = tiny)
                            }
                            // 3. Fuel
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("GAS", style = tiny)
                                VerticalSlider(value = DashboardTestState.fuelLevel, onValueChange = { DashboardTestState.fuelLevel = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f).width(20.dp))
                                Text("${(DashboardTestState.fuelLevel * 100).toInt()}%", style = tiny)
                            }
                            // 4. Brightness
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("DIM", style = tiny)
                                VerticalSlider(value = DashboardTestState.ambientBrightness, onValueChange = { 
                                    DashboardTestState.ambientBrightness = it 
                                    val lp = activity.window.attributes
                                    lp.screenBrightness = it.coerceIn(0.01f, 1.0f)
                                    activity.window.attributes = lp
                                }, valueRange = 0f..1f, modifier = Modifier.weight(1f).width(20.dp))
                                Text("${(DashboardTestState.ambientBrightness * 100).toInt()}%", style = tiny)
                            }
                        }

                        VerticalDivider(Modifier.padding(horizontal = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Right Side: 4 horizontal rows (50% width)
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                            // 5. Screen / Ext
                            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                Text("SCR: ${(DashboardTestState.actualScreenBrightness * 100).toInt()}%", style = tiny)
                                Text("EXT: ${(DashboardTestState.externalLightIntensity * 100).toInt()}%", style = tiny)
                            }
                            // 6. Oil / Neutral
                            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("OIL", style = tiny)
                                    Switch(checked = DashboardTestState.oil, onCheckedChange = { DashboardTestState.oil = it; if (oilInNum in 1..10) ble.sendCommand("IN:$oilInNum:${if (it) 1 else 0}") }, modifier = Modifier.scale(0.5f).height(16.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("NEUT", style = tiny)
                                    Switch(checked = DashboardTestState.neutral, onCheckedChange = { DashboardTestState.neutral = it; if (neutralInNum in 1..10) ble.sendCommand("IN:$neutralInNum:${if (it) 1 else 0}") }, modifier = Modifier.scale(0.5f).height(16.dp))
                                }
                            }
                            // 7. Beams – dwa IN → LOW+HI; jeden wspólny IN → LIGHTS
                            val lightsRows = rows.filter { it.title == "LIGHTS" && it.inNum in 1..10 }
                            val lightsIns = lightsRows.map { it.inNum }.distinct()
                            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                if (lightsIns.size >= 2) {
                                    val rowHi = lightsRows.find { it.subtitle?.contains("HI", true) == true }
                                        ?: lightsRows.minByOrNull { it.inNum }
                                    val rowLow = lightsRows.find { it.subtitle?.contains("LOW", true) == true }
                                        ?: lightsRows.maxByOrNull { it.inNum }
                                    DashboardTestButton("LOW", rowLow, ble)
                                    DashboardTestButton("HI", rowHi, ble)
                                } else {
                                    DashboardTestButton("LIGHTS", lightsRows.firstOrNull(), ble)
                                }
                            }
                            // 8. Turns
                            val rowL = rows.find { it.mode == 2 }
                            val rowP = rows.find { it.mode == 3 }
                            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                DashboardTestButton("L", rowL, ble)
                                DashboardTestButton("P", rowP, ble)
                            }
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
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier
            .graphicsLayer {
                rotationZ = -90f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}

@Composable
fun SmithsGauge(rpm: Float, speed: Float, leftTurnLevel: Float, rightTurnLevel: Float, lowBeamOn: Boolean, hiBeamOn: Boolean, neutralOn: Boolean, oilOn: Boolean, fuelLevel: Float, ambientBrightness: Float) {
    val bgOff = painterResource(id = R.drawable.tachometer_0)
    val bgOn = painterResource(id = R.drawable.tachometer_1)
    val tachoBkg = painterResource(id = R.drawable.tachometer_bkg)
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
    val capBkgPainter = painterResource(id = R.drawable.cap_bkg)

    val fuelNeedlePainter = painterResource(id = R.drawable.fuel_needle)
    val fuelNeedleBkgPainter = painterResource(id = R.drawable.fuel_needle_bkg)

    // Shiver animation for the cap
    val capShiver = remember { Animatable(0f) }
    var lastNeedleAngle by remember { mutableFloatStateOf(0f) }
    var isMoving by remember { mutableStateOf(false) }
    
    val constrainedRpm = rpm.coerceIn(0f, 12000f)
    val startAngle = 145f 
    val sweepAngle = 250f
    val currentNeedleAngle = startAngle + (constrainedRpm / 12000f) * sweepAngle

    LaunchedEffect(currentNeedleAngle) {
        val delta = currentNeedleAngle - lastNeedleAngle
        if (Math.abs(delta) > 0.1f) {
            isMoving = true
        }
        lastNeedleAngle = currentNeedleAngle
        delay(100)
        isMoving = false
    }

    LaunchedEffect(isMoving) {
        if (isMoving) {
            while (true) {
                val target = (Math.random().toFloat() * 3f - 1.5f)
                capShiver.snapTo(target)
                delay(30)
            }
        } else {
            capShiver.animateTo(0f, tween(100))
        }
    }

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

        // 1.5 Layer: Fuel Needle (between fuel level and tacho)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val intrinsicSize = bgOff.intrinsicSize
            if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                val scale = minOf(size.width / intrinsicSize.width, size.height / intrinsicSize.height)
                val dx = (size.width - intrinsicSize.width * scale) / 2f
                val dy = (size.height - intrinsicSize.height * scale) / 2f

                withTransform({
                    translate(dx, dy)
                    scale(scale, scale, Offset.Zero)
                }) {
                    val fuelPivot = Offset(intrinsicSize.width * 0.5f, intrinsicSize.height * 0.735f)
                    val fuelRotation = (0.5f - fuelLevel) * 52f
                    
                    // Fuel Needle BKG
                    rotate(fuelRotation, fuelPivot) {
                        with(fuelNeedleBkgPainter) {
                            draw(size = intrinsicSize, alpha = 1.0f)
                        }
                    }                    // Fuel Needle (Dynamic Alpha)

                    rotate(fuelRotation, fuelPivot) {
                        with(fuelNeedlePainter) {
                            draw(size = intrinsicSize, alpha = ambientBrightness)
                        }
                    }
                }
            }
        }

        // 1.7 Layer: Tachometer Static Background
        Image(
            painter = tachoBkg,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            alpha = 1.0f
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
            val intrinsicSize = bgOff.intrinsicSize
            if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                val scale = minOf(size.width / intrinsicSize.width, size.height / intrinsicSize.height)
                val dx = (size.width - intrinsicSize.width * scale) / 2f
                val dy = (size.height - intrinsicSize.height * scale) / 2f

                withTransform({
                    translate(dx, dy)
                    scale(scale, scale, Offset.Zero)
                }) {
                    // Set pivot point to exact center of the component
                    val center = Offset(intrinsicSize.width / 2f, intrinsicSize.height / 2f)

                    // 2. Speed Display (LCD Window)
                    val sp = Paint().apply { 
                        color = Color(0xFFC5D1C5).toArgb() 
                        textSize = 34.sp.toPx()
                        textAlign = Paint.Align.RIGHT
                        typeface = Typeface.create("monospace", Typeface.NORMAL)
                    }
                    val textCenterShift = (sp.descent() + sp.ascent()) / 2f
                    drawIntoCanvas { 
                        it.nativeCanvas.drawText(speed.toInt().toString(), center.x + 27.dp.toPx(), intrinsicSize.height * 0.636f - textCenterShift, sp)
                    }

                    // 3. Icons
                    val iconSize = intrinsicSize 
                    with(if (leftTurnLevel > 0.5f) left1 else left0) { draw(size = iconSize, alpha = if (leftTurnLevel > 0.5f) 1f else ambientBrightness) }
                    with(if (rightTurnLevel > 0.5f) right1 else right0) { draw(size = iconSize, alpha = if (rightTurnLevel > 0.5f) 1f else ambientBrightness) }
                    with(if (hiBeamOn) hi1 else hi0) { draw(size = iconSize, alpha = if (hiBeamOn) 1f else ambientBrightness) }
                    with(if (lowBeamOn) low1 else low0) { draw(size = iconSize, alpha = if (lowBeamOn) 1f else ambientBrightness) }
                    with(if (oilOn) oil1 else oil0) { draw(size = iconSize, alpha = if (oilOn) 1f else ambientBrightness) }
                    with(if (neutralOn) neutral1 else neutral0) { draw(size = iconSize, alpha = if (neutralOn) 1f else ambientBrightness) }
                    with(if (fuelLevel < 0.15f) fuel1 else fuel0) { draw(size = iconSize, alpha = if (fuelLevel < 0.15f) 1f else ambientBrightness) }

                    // 5. RPM Needle
                    val rotationDegrees = currentNeedleAngle - 270f

                    // Shadow: Always 20px vertically below the needle, pivot on vertical axis
                    translate(0f, 20f) {
                        rotate(rotationDegrees, center) {
                            with(needleBkg1) {
                                draw(size = intrinsicSize, alpha = 0.2f * ambientBrightness, colorFilter = ColorFilter.tint(Color.Black))
                            }
                            with(needlePainter) {
                                draw(size = intrinsicSize, alpha = 0.3f * ambientBrightness, colorFilter = ColorFilter.tint(Color.Black))
                            }
                        }
                    }
                    
                    // Needle Background - Ambient (Off)
                    rotate(rotationDegrees, center) {
                        with(needleBkg0) {
                            draw(size = intrinsicSize, alpha = 1.0f)
                        }
                    }
                    
                    // Needle Background - Backlit (On)
                    rotate(rotationDegrees, center) {
                        with(needleBkg1) {
                            draw(size = intrinsicSize, alpha = backlightAlpha.value)
                        }
                    }
                    
                    // Needle: Drawn on top of shadow and background
                    rotate(rotationDegrees, center) {
                        with(needlePainter) {
                            draw(size = intrinsicSize, alpha = ambientBrightness)
                        }
                    }
                    
                    // 6. Center Cap
                    rotate(capShiver.value, center) {
                        with(capBkgPainter) {
                            draw(size = intrinsicSize, alpha = 1.0f)
                        }
                        with(capPainter) {
                            draw(size = intrinsicSize, alpha = ambientBrightness)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTestButton(
    label: String,
    row: ControlInRow?,
    ble: com.yamahub.app.BleManager
) {
    var pressed by remember { mutableStateOf(false) }
    val currentBle by rememberUpdatedState(ble)
    val currentRow by rememberUpdatedState(row)

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(width = 44.dp, height = 21.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val inNum = currentRow?.inNum ?: return@detectTapGestures
                        if (inNum !in 1..10) return@detectTapGestures
                        pressed = true
                        ControlBlinkers.onDown(currentBle, inNum)
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                            ControlBlinkers.onUp(currentBle, inNum)
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
