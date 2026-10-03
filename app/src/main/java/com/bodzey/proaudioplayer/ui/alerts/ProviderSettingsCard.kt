package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderSettingsCard(
    settings: AlertProviderSettings,
    form: AlertProviderForm,
    message: AlertsMessage?,
    busyAction: AlertsBusyAction?,
    onFormChange: (AlertProviderForm) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val busy = busyAction != null
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var advancedVisible by rememberSaveable { mutableStateOf(false) }
    var locationMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message?.isError == true) advancedVisible = true
    }

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.alerts_provider_description),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    if (settings.tokenConfigured) R.string.alerts_token_configured else R.string.alerts_token_missing,
                ),
                color = if (settings.tokenConfigured) colors.success else colors.warning,
                style = MaterialTheme.typography.labelMedium,
            )

            AlertTextField(
                value = form.locationUid,
                onValueChange = { value ->
                    onFormChange(form.copy(locationUid = value))
                },
                label = stringResource(R.string.alerts_location_uid),
                enabled = !busy,
                keyboardType = KeyboardType.Number,
            )

            ExposedDropdownMenuBox(
                expanded = locationMenuExpanded,
                onExpandedChange = {
                    if (!busy) locationMenuExpanded = !locationMenuExpanded
                },
            ) {
                OutlinedTextField(
                    value = locationTypeLabel(form.locationType),
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    enabled = !busy,
                    readOnly = true,
                    label = {
                        Text(stringResource(R.string.alerts_location_type))
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = locationMenuExpanded,
                        )
                    },
                )
                ExposedDropdownMenu(
                    expanded = locationMenuExpanded,
                    onDismissRequest = { locationMenuExpanded = false },
                ) {
                    LOCATION_TYPES.forEach { value ->
                        DropdownMenuItem(
                            enabled = !busy,
                            text = {
                                Text(locationTypeLabel(value))
                            },
                            onClick = {
                                locationMenuExpanded = false
                                onFormChange(form.copy(locationType = value))
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.token,
                onValueChange = { value ->
                    onFormChange(form.copy(token = value))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
                label = {
                    Text(stringResource(R.string.alerts_new_token))
                },
                supportingText = {
                    Text(stringResource(R.string.alerts_token_keep_hint))
                },
                visualTransformation = if (tokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = { tokenVisible = !tokenVisible },
                        enabled = !busy,
                    ) {
                        Text(
                            text = stringResource(
                                if (tokenVisible) {
                                    R.string.alerts_hide_token
                                } else {
                                    R.string.alerts_show_token
                                },
                            ),
                        )
                    }
                },
            )

            TextButton(
                onClick = { advancedVisible = !advancedVisible },
                enabled = !busy,
            ) {
                Text(
                    text = stringResource(
                        if (advancedVisible) {
                            R.string.alerts_advanced_hide
                        } else {
                            R.string.alerts_advanced_show
                        },
                    ),
                )
            }

            if (advancedVisible) {
                AlertTextField(
                    value = form.endpoint,
                    onValueChange = { onFormChange(form.copy(endpoint = it)) },
                    label = stringResource(R.string.alerts_endpoint),
                    enabled = !busy,
                    supportingText = stringResource(R.string.alerts_endpoint_hint),
                    keyboardType = KeyboardType.Uri,
                )
                AlertTextField(
                    value = form.pollIntervalSeconds,
                    onValueChange = { onFormChange(form.copy(pollIntervalSeconds = it)) },
                    label = stringResource(R.string.alerts_poll_interval),
                    enabled = !busy,
                    keyboardType = KeyboardType.Decimal,
                )
                AlertTextField(
                    value = form.requestTimeoutSeconds,
                    onValueChange = { onFormChange(form.copy(requestTimeoutSeconds = it)) },
                    label = stringResource(R.string.alerts_request_timeout),
                    enabled = !busy,
                    keyboardType = KeyboardType.Decimal,
                )
                AlertTextField(
                    value = form.rateLimitBackoffSeconds,
                    onValueChange = { onFormChange(form.copy(rateLimitBackoffSeconds = it)) },
                    label = stringResource(R.string.alerts_rate_limit_backoff),
                    enabled = !busy,
                    keyboardType = KeyboardType.Decimal,
                )
                AlertTextField(
                    value = form.clearConfirmations,
                    onValueChange = { onFormChange(form.copy(clearConfirmations = it)) },
                    label = stringResource(R.string.alerts_clear_confirmations),
                    enabled = !busy,
                    keyboardType = KeyboardType.Number,
                )
            }
        }
    }
}

@Composable
private fun locationTypeLabel(value: String): String =
    when (value) {
        "hromada" -> stringResource(R.string.alerts_location_hromada)
        "city" -> stringResource(R.string.alerts_location_city)
        "raion" -> stringResource(R.string.alerts_location_raion)
        "oblast" -> stringResource(R.string.alerts_location_oblast)
        "standalone" -> stringResource(R.string.alerts_location_standalone)
        else -> value
    }

private val LOCATION_TYPES =
    listOf("hromada", "city", "raion", "oblast", "standalone")
