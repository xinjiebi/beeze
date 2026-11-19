package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BeezeColors.Surface,
        scrimColor = BeezeColors.Scrim,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(60.dp, 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BeezeColors.Muted),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                title,
                style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                color = BeezeColors.Body,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = BeezeType.bodyMd,
                        color = if (value == selected) BeezeColors.Primary else BeezeColors.Body,
                        modifier = Modifier.weight(1f),
                    )
                    if (value == selected) {
                        SelectionIndicator(true)
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
