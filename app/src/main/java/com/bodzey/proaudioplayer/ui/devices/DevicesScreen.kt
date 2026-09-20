package com.bodzey.proaudioplayer.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.device.DeviceListEntry
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.ui.components.ProAudioHeader
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.ProAudioShell
import com.bodzey.proaudioplayer.ui.components.StatusBadge
import com.bodzey.proaudioplayer.ui.components.StatusBadgeState
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
fun DevicesScreen(
    devices: List<DeviceListEntry>,
    demoEnabled: Boolean,
    showDemoControls: Boolean,
    forgettingDeviceId: DeviceId?,
    onDemoEnabledChange: (Boolean) -> Unit,
    onDeviceSelected: (DeviceId) -> Unit,
    onDeviceForget: (DeviceId) -> Unit,
) {
    val colors = LocalProAudioColors.current
    var pendingForgetId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingForgetDevice = devices.firstOrNull { device ->
        device.id.value == pendingForgetId && !device.online
    }

    LaunchedEffect(
        pendingForgetId,
        pendingForgetDevice,
        forgettingDeviceId,
    ) {
        if (pendingForgetId != null &&
            pendingForgetDevice == null &&
            forgettingDeviceId?.value != pendingForgetId
        ) {
            pendingForgetId = null
        }
    }

    ProAudioShell {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 2.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProAudioHeader(
                    trailing = {
                        StatusBadge(
                            text = if (devices.none { device -> device.online }) {
                                stringResource(R.string.devices_scanning)
                            } else {
                                stringResource(R.string.device_online)
                            },
                            state = if (devices.none { device -> device.online }) {
                                StatusBadgeState.Connecting
                            } else {
                                StatusBadgeState.Online
                            },
                        )
                    },
                )
            }

            item {
                Column(
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.devices_title),
                        color = colors.text,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.devices_subtitle),
                        color = colors.textSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (devices.isEmpty()) {
                item {
                    EmptyDevicesState()
                }
            } else {
                items(
                    items = devices,
                    key = { device -> device.id.value },
                ) { device ->
                    DeviceCard(
                        device = device,
                        forgetting = forgettingDeviceId == device.id,
                        onClick = { onDeviceSelected(device.id) },
                        onForget = { pendingForgetId = device.id.value },
                    )
                }
            }

            if (showDemoControls) {
                item {
                    DemoControl(
                        enabled = demoEnabled,
                        onEnabledChange = onDemoEnabledChange,
                    )
                }
            }
        }
    }

    pendingForgetDevice?.let { device ->
        val forgetting = forgettingDeviceId == device.id
        AlertDialog(
            onDismissRequest = {
                if (!forgetting) {
                    pendingForgetId = null
                }
            },
            title = {
                Text(stringResource(R.string.device_forget_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.device_forget_message,
                        device.displayName,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onDeviceForget(device.id) },
                    enabled = forgettingDeviceId == null,
                ) {
                    Text(stringResource(R.string.device_forget))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingForgetId = null },
                    enabled = !forgetting,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptyDevicesState() {
    val colors = LocalProAudioColors.current

    ProAudioPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.devices_searching),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.devices_searching_support),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceListEntry,
    forgetting: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    val colors = LocalProAudioColors.current

    ProAudioPanel(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.surfaceRaised, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerGlyph()
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = device.displayName,
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (device.online) colors.success else colors.textMuted,
                                CircleShape,
                            ),
                    )
                    Text(
                        text = stringResource(
                            if (device.online) {
                                R.string.device_online
                            } else {
                                R.string.device_offline
                            },
                        ),
                        color = colors.textSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(colors.textMuted, CircleShape),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.device_endpoint_count,
                            device.endpoints.size,
                            device.endpoints.size,
                        ),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text(
                    text = stringResource(
                        R.string.device_api_version,
                        device.apiMajorVersion,
                    ),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )

                if (!device.online) {
                    TextButton(
                        onClick = onForget,
                        enabled = !forgetting,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.device_forget))
                    }
                }
            }

            Text(
                text = "›",
                modifier = Modifier.clearAndSetSemantics {},
                color = colors.accent,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
private fun EqualizerGlyph() {
    val colors = LocalProAudioColors.current

    Row(
        modifier = Modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(10, 18, 14, 22, 12).forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = height.dp)
                    .background(
                        if (index % 2 == 0) colors.accent else colors.textSoft,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun DemoControl(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val colors = LocalProAudioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange,
            )
            .padding(horizontal = 4.dp)
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.demo_devices),
                color = colors.textSoft,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.demo_devices_hint),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = null,
        )
    }
}
