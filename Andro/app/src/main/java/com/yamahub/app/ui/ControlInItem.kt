package com.yamahub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier

@Composable
fun ControlInItem(
    row: ControlInRow,
    levelForOut: (Int) -> Float,
    enabled: Boolean,
    isDragging: Boolean,
    gapShiftY: Float,
    dragOffsetY: Float = 0f,
    onDown: () -> Unit,
    onUp: (heldMs: Long) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val unused = row.mode < 0
    var pressed by remember { mutableStateOf(false) }
    var downAt by remember { mutableLongStateOf(0L) }

    val cardBg by animateColorAsState(
        when {
            isDragging -> MaterialTheme.colorScheme.primaryContainer
            unused -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
            pressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        label = "card${row.primaryOut}"
    )
    val elev by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elev${row.primaryOut}")

    Row(
        Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragOffsetY
                    shadowElevation = 12f
                    alpha = 0.95f
                } else if (gapShiftY != 0f) {
                    translationY = gapShiftY
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Etykieta z uchwytem DnD w środku (jak InputSettings)
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .shadow(elev, RoundedCornerShape(10.dp))
                .background(cardBg, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = if (unused) 0.15f else 0.25f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Uchwyt DnD – wewnątrz karty
            Box(
                Modifier
                    .size(44.dp)
                    .pointerInput(row.primaryOut) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Przenieś",
                    tint = when {
                        unused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        isDragging -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Tekst – sterowanie (press), bez DnD
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (!unused && enabled && row.title != "NEUTRAL") {
                            Modifier.pointerInput(row.primaryOut, enabled) {
                                detectTapGestures(
                                    onPress = {
                                        pressed = true
                                        downAt = System.currentTimeMillis()
                                        onDown()
                                        tryAwaitRelease()
                                        pressed = false
                                        onUp(System.currentTimeMillis() - downAt)
                                    }
                                )
                            }
                        } else Modifier
                    )
                    .padding(end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (unused) "—" else row.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (unused)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                if (!unused && row.subtitle != null) {
                    Text(
                        row.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Kwadrat OUT – zawsze OUT_xx
        row.outNums.forEachIndexed { i, out ->
            if (i > 0) Spacer(Modifier.width(6.dp))
            OutSquare(
                label = "OUT %02d".format(out),
                level = if (unused) 0f else levelForOut(out),
                onColor = colorForRow(row, i)
            )
        }
    }
}
