package com.yamahub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yamahub.app.BleHub
import com.yamahub.app.InputCfgItem
import com.yamahub.app.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MODE_LABELS = listOf(
    "Toggle",
    "Moment",
    "Kierunek L",
    "Kierunek P",
    "Sensor",
    "Wyłączone"   // index 5
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSettingsTab() {
    val context = LocalContext.current
    val ble = remember { BleHub.manager(context) }
    val scope = rememberCoroutineScope()

    var saved by remember {
        mutableStateOf((1..9).map { InputCfgItem(it, 0, it, "IN_$it") })
    }
    var draft by remember { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // które pozycje są rozwinięte
    var expanded by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val dirty = draft != saved

    DisposableEffect(Unit) {
        val prev = ble.onInputCfg
        ble.onInputCfg = { list ->
            if (list.size == 9) {
                saved = list
                if (!saving) {
                    draft = list
                } else {
                    draft = list
                    saved = list
                    saving = false
                }
                error = null
            }
            prev?.invoke(list)
        }
        onDispose { ble.onInputCfg = prev }
    }

    LaunchedEffect(Unit) {
        delay(300)
        if (ble.isConnected) {
            ble.requestInputCfg()
        }
    }

    fun saveAll() {
        val l = draft.count { it.mode == 2 }
        val r = draft.count { it.mode == 3 }
        if (l !in 1..2 || r !in 1..2) {
            error = "Wymagane: 1–2× Kierunek L i 1–2× Kierunek P (teraz L=$l P=$r)"
            return
        }
        error = null
        saving = true
        val toSave = draft.toList()
        scope.launch {
            try {
                for (item in toSave) {
                    ble.setInputCfg(item.inNum, item.mode, item.outNum, item.name)
                    delay(150)
                }
                delay(400)
                ble.requestInputCfg()
                delay(500)
                saved = toSave
                draft = toSave
            } finally {
                saving = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(draft, key = { it.inNum }) { item ->
                val index = draft.indexOfFirst { it.inNum == item.inNum }
                val isOpen = item.inNum in expanded

                var modeExpanded by remember { mutableStateOf(false) }
                var outExpanded by remember { mutableStateOf(false) }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // --- zwinięty nagłówek: nazwa + strzałka ---
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = if (isOpen) {
                                        expanded - item.inNum
                                    } else {
                                        expanded + item.inNum
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayName(item.name).ifBlank { "IN_${item.inNum}" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (!isOpen) {
                                    Text(
                                        "IN_${item.inNum} · ${MODE_LABELS.getOrElse(item.mode) { "?" }} · OUT_${item.outNum}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(
                                if (isOpen) "▲" else "▼",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // --- rozwinięta edycja ---
                        if (isOpen) {
                            OutlinedTextField(
                                value = displayName(item.name),
                                onValueChange = { v ->
                                    draft = draft.toMutableList().also {
                                        it[index] = item.copy(name = v.take(15))
                                    }
                                    error = null
                                },
                                label = { Text("Nazwa") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Tryb
                            ExposedDropdownMenuBox(
                                expanded = modeExpanded,
                                onExpandedChange = { modeExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = MODE_LABELS.getOrElse(item.mode) { "?" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Tryb") },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded)
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = modeExpanded,
                                    onDismissRequest = { modeExpanded = false }
                                ) {
                                    MODE_LABELS.forEachIndexed { idx, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                modeExpanded = false
                                                draft = draft.toMutableList().also {
                                                    it[index] = item.copy(mode = idx)
                                                }
                                                error = null
                                            }
                                        )
                                    }
                                }
                            }

                            // OUT
                            ExposedDropdownMenuBox(
                                expanded = outExpanded,
                                onExpandedChange = { outExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = "OUT_${item.outNum}",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Wyjście") },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(outExpanded)
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = outExpanded,
                                    onDismissRequest = { outExpanded = false }
                                ) {
                                    (1..9).forEach { o ->
                                        DropdownMenuItem(
                                            text = { Text("OUT_$o") },
                                            onClick = {
                                                outExpanded = false
                                                draft = draft.toMutableList().also {
                                                    it[index] = item.copy(outNum = o)
                                                }
                                                error = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { saveAll() },
            enabled = dirty && !saving && ble.isConnected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Zapisywanie…" else "Zapisz ustawienia")
        }
    }
}