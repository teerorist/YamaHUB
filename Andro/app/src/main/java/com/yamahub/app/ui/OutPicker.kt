package com.yamahub.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutPicker(
    label: String,
    selected: Int,
    outOccupants: Map<Int, List<String>>,
    excludeSelf: Set<Int>,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        CompactOutlinedTextField(
            value = if (selected in 1..10) "OUT %02d".format(selected) else "—",
            onValueChange = {},
            readOnly = true,
            label = label,
            textStyle = MaterialTheme.typography.titleSmall,
            modifier = modifier.menuAnchor().fillMaxWidth().height(52.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (allowNone) {
                DropdownMenuItem(
                    modifier = Modifier.height(52.dp),
                    text = { Text("—") },
                    onClick = { expanded = false; onSelect(0) }
                )
            }
            (1..10).forEach { o ->
                val holders = outOccupants[o].orEmpty()
                val takenByOther = holders.isNotEmpty() && o !in excludeSelf
                val text = if (holders.isNotEmpty()) {
                    "OUT %02d  · ".format(o) + holders.joinToString(", ")
                } else {
                    "OUT %02d".format(o)
                }
                DropdownMenuItem(
                    modifier = Modifier.height(52.dp),
                    text = {
                        Text(
                            text = text,
                            color = if (takenByOther)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            else
                                LocalContentColor.current
                        )
                    },
                    enabled = true,
                    onClick = {
                        expanded = false
                        onSelect(o)
                    }
                )
            }
        }
    }
}
