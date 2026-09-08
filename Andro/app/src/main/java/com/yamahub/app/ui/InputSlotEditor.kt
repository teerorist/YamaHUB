package com.yamahub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SlotEditor(
    slot: FnSlot,
    modeDefinitions: List<com.yamahub.app.ModeDefinition>,
    outOccupants: Map<Int, List<String>>,
    lightsCount: Int,
    hasKillSwitch: Boolean = false,
    hideKinds: Set<FnKind> = emptySet(),
    onChange: (FnSlot) -> Unit,
    onPersist: (FnSlot) -> Unit = onChange,
    onChangeCategory: (FnCategory) -> Unit,
    onChangeKind: (FnKind) -> Unit
) {
    val h = 46.dp
    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!slot.isFixed) {
            // 1. Typ (Category)
            var categoryExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                CompactOutlinedTextField(
                    value = slot.category.name,
                    onValueChange = {},
                    readOnly = true,
                    label = "Type",
                    modifier = Modifier.menuAnchor().fillMaxWidth().height(h),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) }
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    listOf(FnCategory.BUTTON, FnCategory.SENSOR, FnCategory.DISABLED).forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = { categoryExpanded = false; onChangeCategory(category) }
                        )
                    }
                }
            }

            // 2. Funkcja (Kind)
            if (slot.category != FnCategory.DISABLED) {
                var kindExpanded by remember { mutableStateOf(false) }
                
                val options = modeDefinitions
                    .filter { it.category == slot.category.ordinal }
                    .mapNotNull { def ->
                        val kind = FnKind.entries.find { k -> k.id == def.id } ?: return@mapNotNull null
                        if (kind != FnKind.USER && kind != FnKind.DISABLED && kind in hideKinds) {
                            return@mapNotNull null
                        }
                        val addable = def.flags == 1 ||
                            kind == FnKind.LIGHTS_2 || kind == FnKind.BRAKE_2
                        if (!addable) return@mapNotNull null
                        kind to kindPickerLabel(kind, def.label)
                    }

                val currentLabel = kindPickerLabel(
                    slot.kind,
                    modeDefinitions.find {
                        it.id == slot.kind.id && it.category == slot.category.ordinal
                    }?.label
                )

                ExposedDropdownMenuBox(
                    expanded = kindExpanded,
                    onExpandedChange = { kindExpanded = it }
                ) {
                    CompactOutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = "Function",
                        modifier = Modifier.menuAnchor().fillMaxWidth().height(h),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) }
                    )
                    ExposedDropdownMenu(expanded = kindExpanded, onDismissRequest = { kindExpanded = false }) {
                        options.forEach { (k, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { kindExpanded = false; onChangeKind(k) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Nazwa (Custom Name dla USER)
        if (slot.kind == FnKind.USER) {
            val requester = remember { BringIntoViewRequester() }
            val scope = rememberCoroutineScope()
            CompactOutlinedTextField(
                value = slot.customName,
                onValueChange = { onChange(slot.copy(customName = it.take(15))) },
                label = "Name",
                placeholder = slot.category.name,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth().height(h)
                    .bringIntoViewRequester(requester)
                    .onFocusEvent { if (it.isFocused) scope.launch { delay(300); requester.bringIntoView() } }
            )
        }

        // 4. Porty OUT: BUTTON zawsze Output; SENSOR (poza BRAKE) — checkbox
        if (slot.kind != FnKind.DISABLED && !slot.isOutLocked) {
            val sensorOptional = slot.category == FnCategory.SENSOR &&
                slot.kind != FnKind.BRAKE_1 && slot.kind != FnKind.BRAKE_2
            if (slot.kind == FnKind.KILL_SWITCH) {
                OutPicker(
                    label = "Output",
                    selected = slot.outPrimary,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outPrimary),
                    onSelect = { onPersist(slot.copy(outPrimary = it, outputEnabled = true)) }
                )
            } else if (slot.kind == FnKind.LIGHTS_1 && lightsCount < 2) {
                OutPicker(
                    label = "Output (Hi Beam)",
                    selected = slot.outPrimary,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outPrimary, slot.outSecondary),
                    onSelect = { onPersist(slot.copy(outPrimary = it)) }
                )
                Spacer(Modifier.height(6.dp))
                OutPicker(
                    label = "Output (Low Beam)",
                    selected = slot.outSecondary,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outPrimary, slot.outSecondary),
                    onSelect = { onPersist(slot.copy(outSecondary = it)) }
                )
            } else if (slot.kind == FnKind.STARTER && !hasKillSwitch) {
                OutPicker(
                    label = "Output (Starter)",
                    selected = slot.outPrimary,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outPrimary, slot.outSecondary),
                    onSelect = { onPersist(slot.copy(outPrimary = it)) }
                )
                Spacer(Modifier.height(6.dp))
                OutPicker(
                    label = "Output (Kill Switch)",
                    selected = slot.outSecondary,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(slot.outPrimary, slot.outSecondary),
                    onSelect = { onPersist(slot.copy(outSecondary = it)) }
                )
            } else if (sensorOptional) {
                val selected = if (slot.kind == FnKind.USER) slot.outputIndex else slot.outPrimary
                Row(modifier = Modifier.height(h), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = slot.outputEnabled,
                        onCheckedChange = {
                            onPersist(
                                if (slot.kind == FnKind.USER) slot.copy(outputEnabled = it)
                                else slot.copy(outputEnabled = it, outPrimary = if (it) slot.outPrimary else 0)
                            )
                        }
                    )
                    if (slot.outputEnabled) {
                        OutPicker(
                            label = "Output",
                            selected = selected,
                            outOccupants = outOccupants,
                            excludeSelf = setOf(selected),
                            onSelect = {
                                onPersist(
                                    if (slot.kind == FnKind.USER)
                                        slot.copy(outputIndex = it, outputEnabled = true)
                                    else slot.copy(outPrimary = it, outputEnabled = true)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text("Manage Output Port", fontSize = 12.sp)
                    }
                }
            } else {
                OutPicker(
                    label = "Output",
                    selected = if (slot.kind == FnKind.USER) slot.outputIndex else slot.outPrimary,
                    outOccupants = outOccupants,
                    excludeSelf = setOf(
                        if (slot.kind == FnKind.USER) slot.outputIndex else slot.outPrimary
                    ),
                    onSelect = {
                        onPersist(
                            if (slot.kind == FnKind.USER)
                                slot.copy(outputIndex = it, outputEnabled = true)
                            else slot.copy(outPrimary = it, outputEnabled = true)
                        )
                    }
                )
            }
        }
    }
}
