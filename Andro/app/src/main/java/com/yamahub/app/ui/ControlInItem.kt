package com.yamahub.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun ControlInItem(
    row: ControlInRow,
    levelForOut: (Int) -> Float,
    enabled: Boolean,
    isDragging: Boolean,
    gapShiftY: Float,
    dragOffsetY: Float = 0f,
    onOutTap: (() -> Unit)? = null,
    onDragStart: () -> Unit,
    onDrag: (dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val isUnused = row.inNum == 0

    val cardBg by animateColorAsState(
        when {
            isDragging -> MaterialTheme.colorScheme.primaryContainer
            isUnused -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        label = "card${row.primaryOut}"
    )
    val elev by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elev${row.primaryOut}")

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .zIndex(if (isDragging) 10f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragOffsetY else gapShiftY
                    shadowElevation = if (isDragging) 12f else 0f
                    alpha = if (isDragging) 0.95f else 1f
                }
                .shadow(elev, RoundedCornerShape(10.dp))
                .background(cardBg, RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = if (isUnused) 0.15f else 0.25f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    contentDescription = null,
                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (isUnused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface
                )
                if (row.subtitle != null) {
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

        OutSquare(
            label = "OUT %02d".format(row.primaryOut),
            level = if (isUnused) 0f else levelForOut(row.primaryOut),
            onColor = colorForRow(row),
            isAssigned = row.inNum > 0,
            onTap = if (!isUnused && enabled) onOutTap else null
        )
    }
}
