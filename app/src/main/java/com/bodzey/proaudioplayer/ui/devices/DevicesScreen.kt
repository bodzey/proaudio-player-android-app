package com.bodzey.proaudioplayer.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint

@Composable
fun DevicesScreen(
    devices: List<AvailableDevice>,
    demoEnabled: Boolean,
    showDemoControls: Boolean,
    onDemoEnabledChange: (Boolean) -> Unit,
    onDeviceSelected: (com.bodzey.proaudioplayer.core.model.DeviceId) -> Unit,
) {
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.devices_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            if (showDemoControls) {
                DemoControl(
                    enabled = demoEnabled,
                    onEnabledChange = onDemoEnabledChange,
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (devices.isEmpty()) {
                EmptyDevicesState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = devices,
                        key = { device -> device.id.value },
                    ) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceSelected(device.id) },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoControl(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.demo_devices),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }

    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun EmptyDevicesState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.devices_searching),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.devices_searching_support),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceCard(
    device: AvailableDevice,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OnlineStatus()
                }

                Text(
                    text = stringResource(
                        R.string.device_api_version,
                        device.apiMajorVersion,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = pluralStringResource(
                    R.plurals.device_endpoint_count,
                    device.endpoints.size,
                    device.endpoints.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            device.endpoints
                .sortedWith(
                    compareBy<DeviceEndpoint> { endpoint -> endpoint.host }
                        .thenBy { endpoint -> endpoint.port },
                )
                .forEach { endpoint ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = endpoint.displayValue(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
        }
    }
}

@Composable
private fun OnlineStatus() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        )
        Text(
            text = stringResource(R.string.device_online),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DeviceEndpoint.displayValue(): String {
    val formattedHost = if (':' in host) "[$host]" else host
    return "$formattedHost:$port"
}
