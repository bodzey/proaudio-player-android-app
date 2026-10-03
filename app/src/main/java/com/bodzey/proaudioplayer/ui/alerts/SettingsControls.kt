package com.bodzey.proaudioplayer.ui.alerts

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalProAudioColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 4.dp))
        Text(title, color = colors.text, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
internal fun SettingsSlider(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    enabled: Boolean,
    supportingText: String? = null,
) {
    val colors = LocalProAudioColors.current
    val number = settingsNumber(value)?.takeIf { it >= range.start && it <= range.endInclusive }
    var editing by rememberSaveable { mutableStateOf(false) }
    var entered by rememberSaveable { mutableStateOf("") }
    val preciseLabel = stringResource(R.string.settings_precise_value, label)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = colors.text,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(
                onClick = {
                    entered = value
                    editing = true
                },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = preciseLabel },
            ) {
                Text("$value $unit", color = if (number == null) colors.danger else colors.accent)
            }
        }
        Slider(
            value = number?.toFloat() ?: range.start,
            onValueChange = { onValueChange(settingsSliderValue(it)) },
            valueRange = range,
            enabled = enabled && number != null,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = "$value $unit"
            },
        )
        if (supportingText != null || number == null) {
            Text(
                text = if (number == null) {
                    stringResource(R.string.settings_invalid_number)
                } else {
                    supportingText.orEmpty()
                },
                color = if (number == null) colors.danger else colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (editing) {
        val next = settingsNumber(entered)?.takeIf { it >= range.start && it <= range.endInclusive }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                AlertTextField(
                    value = entered,
                    enabled = enabled,
                    onValueChange = { entered = it },
                    label = unit,
                    keyboardType = if (range.start < 0) KeyboardType.Ascii else KeyboardType.Decimal,
                    isError = entered.isNotEmpty() && next == null,
                    supportingText = stringResource(
                        R.string.settings_number_range,
                        settingsSliderValue(range.start),
                        settingsSliderValue(range.endInclusive),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        next?.let {
                            onValueChange(it.toString().removeSuffix(".0"))
                            editing = false
                        }
                    },
                    enabled = enabled && next != null,
                ) {
                    Text(stringResource(R.string.settings_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTime(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val colors = LocalProAudioColors.current
    val label = stringResource(R.string.alerts_minute_time)
    val timeLabel = settingsTimeLabel(value)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = colors.text,
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "$label, $timeLabel" },
        ) {
            Text(timeLabel)
        }
    }
    if (showPicker) {
        val previous = settingsTime(value)
        val picker = rememberTimePickerState(
            initialHour = previous?.hour ?: 9,
            initialMinute = previous?.minute ?: 0,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        val configuration = LocalConfiguration.current
        val compact = configuration.screenHeightDp < 500 || configuration.fontScale > 1.3f
        var useDial by rememberSaveable(compact) { mutableStateOf(!compact) }
        var seconds by rememberSaveable { mutableStateOf((previous?.second ?: 0).toString()) }
        val nextSeconds = seconds.toIntOrNull()?.takeIf { it in 0..59 }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (useDial) TimePicker(state = picker) else TimeInput(state = picker)
                    TextButton(onClick = { useDial = !useDial }) {
                        Text(stringResource(if (useDial) R.string.settings_time_keyboard else R.string.settings_time_clock))
                    }
                    AlertTextField(
                        value = seconds,
                        enabled = enabled,
                        onValueChange = { seconds = it },
                        label = stringResource(R.string.settings_time_second),
                        keyboardType = KeyboardType.Number,
                        isError = nextSeconds == null,
                        supportingText = stringResource(R.string.settings_time_second_hint),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        nextSeconds?.let {
                            onValueChange(selectedSettingsTime(value, picker.hour, picker.minute, it))
                            showPicker = false
                        }
                    },
                    enabled = enabled && nextSeconds != null,
                ) {
                    Text(stringResource(R.string.settings_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
