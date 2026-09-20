package com.bodzey.proaudioplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.ui.AppSection
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
fun PrimaryNavigation(
    selected: AppSection,
    onSelected: (AppSection) -> Unit,
    alertActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalProAudioColors.current
    val alertsLabel = stringResource(R.string.nav_alerts)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .background(colors.surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        NavigationItem(
            label = stringResource(R.string.nav_player),
            selected = selected == AppSection.Player,
            onClick = { onSelected(AppSection.Player) },
            modifier = Modifier.weight(1f),
        )
        NavigationItem(
            label = stringResource(R.string.nav_radio),
            selected = selected == AppSection.Radio,
            onClick = { onSelected(AppSection.Radio) },
            modifier = Modifier.weight(1f),
        )
        NavigationItem(
            label = if (alertActive) "$alertsLabel •" else alertsLabel,
            contentDescription = if (alertActive) {
                stringResource(R.string.nav_alerts_active)
            } else {
                alertsLabel
            },
            selected = selected == AppSection.Alerts,
            onClick = { onSelected(AppSection.Alerts) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
) {
    val colors = LocalProAudioColors.current
    val background = if (selected) {
        Brush.verticalGradient(
            listOf(
                colors.accent.copy(alpha = 0.13f),
                colors.accent.copy(alpha = 0.07f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color.Transparent,
            ),
        )
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                this.role = Role.Tab
                this.contentDescription = contentDescription
            },
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = if (selected) {
            BorderStroke(1.dp, colors.accent.copy(alpha = 0.62f))
        } else {
            BorderStroke(1.dp, Color.Transparent)
        },
    ) {
        Row(
            modifier = Modifier
                .background(background)
                .padding(horizontal = 6.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (selected) colors.accent else colors.textMuted,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}
