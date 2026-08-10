package com.yamahub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SlotEditor(
    slot: FnSlot,
    canAddLights2: Boolean,
    canAddBrake2: Boolean,
    lightsCount: Int,
    outOccupants: Map<Int, List<String>>,
    onChange: (FnSlot) -> Unit,
    onChangeKind: (FnKind) -> Unit
) {
    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!slot.isFixed()) {
            var kindExpanded by remember { mutableStateOf(false) }
            val options = buildList {
                add(FnKind.BUTTON to "BUTTON")
                add(FnKind.SENSOR to "SENSOR")
                add(FnKind.DISABLED to "DISABLED")
                if (canAddLights2) add(FnKind.LIGHTS to "LIGHTS 2")
                if (canAddBrake2) add(FnKind.BRAKE to "BRAKE 2")
            }
            ExposedDropdownMenuBox(
                expanded = kindExpanded,
                onExpandedChange = { kindExpanded = it }
            ) {
                OutlinedTextField(
                    value = when (slot.kind) {
                        FnKind.BUTTON -> "BUTTON"
                        FnKind.SENSOR -> "SENSOR"
                        FnKind.DISABLED -> "DISABLED"
                        FnKind.LIGHTS -> "LIGHTS 2"
                        FnKind.BRAKE -> "BRAKE 2"
                        else -> slot.title()
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Funkcja") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = kindExpanded,
                    onDismissRequest = { kindExpanded = false }
                ) {
                    options.forEach { (k, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                kindExpanded = false
                                onChangeKind(k)
                            }
                        )
                    }
                }
            }
        }

        if (slot.kind == FnKind.BUTTON || slot.kind == FnKind.SENSOR) {
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            val scope = rememberCoroutineScope()
            OutlinedTextField(
                value = slot.customName,
                onValueChange = { onChange(slot.copy(customName = it.take(15))) },
                label = { Text("Nazwa") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusEvent { state ->
                        if (state.isFocused) {
                            scope.launch {
                                delay(300)
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    }
            )
        }

        if (slot.hasOutPicker()) {
            val dualLights = slot.kind == FnKind.LIGHTS && lightsCount < 2
            if (dualLights) {
                OutPicker(
                    label = "OUT · LOW BEAM",
                    selected = slot.outNum,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outNum),
                    onSelect = { onChange(slot.copy(outNum = it)) }
                )
                OutPicker(
                    label = "OUT · HI BEAM",
                    selected = slot.outNum2.takeIf { it in 1..10 } ?: 0,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outNum2),
                    onSelect = { onChange(slot.copy(outNum2 = it)) }
                )
            } else {
                val label = when {
                    slot.kind == FnKind.LIGHTS && slot.variant <= 1 -> "OUT · LOW BEAM"
                    slot.kind == FnKind.LIGHTS -> "OUT · HI BEAM"
                    slot.kind == FnKind.BRAKE -> "OUT · BRAKE"
                    slot.kind == FnKind.NEUTRAL -> "OUT · NEUTRAL"
                    else -> "Wyjście"
                }
                OutPicker(
                    label = label,
                    selected = slot.outNum,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outNum),
                    onSelect = { onChange(slot.copy(outNum = it)) }
                )
            }
        }
    }
}
