package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType
import com.syncedlyrics.sdk.SyncedLyricsView

/** 与播放列表共用白色底部弹层、圆角、拖拽把手和 75% 高度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricEffectsSheet(lyricView: SyncedLyricsView, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedLyric by remember { mutableStateOf(LyricThemes.current(context)) }
    var selectedBackground by remember { mutableStateOf(LyricThemes.currentBackground(context)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BeezeColors.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(60.dp, 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(BeezeColors.Hairline)
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                Text("歌词效果", style = BeezeType.bodySm, color = BeezeColors.Ink)
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text("歌词动画", style = BeezeType.bodySm.copy(fontWeight = FontWeight.Medium), color = BeezeColors.Muted)
                Spacer(Modifier.height(12.dp))
                EffectGrid(
                    options = LyricThemes.modes,
                    selected = selectedLyric,
                    onSelect = { mode ->
                        selectedLyric = mode
                        LyricThemes.select(context, mode)
                        lyricView.lyricStyle = mode
                    },
                )
                Spacer(Modifier.height(28.dp))
                Text("背景效果", style = BeezeType.bodySm.copy(fontWeight = FontWeight.Medium), color = BeezeColors.Muted)
                Spacer(Modifier.height(12.dp))
                EffectGrid(
                    options = LyricThemes.backgrounds,
                    selected = selectedBackground,
                    onSelect = { mode ->
                        selectedBackground = mode
                        LyricThemes.selectBackground(context, mode)
                        lyricView.backgroundStyle = mode
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun <T> EffectGrid(options: List<T>, selected: T, onSelect: (T) -> Unit) where T : Enum<T> {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val isSelected = option == selected
                    Box(
                        modifier = Modifier.weight(1f).height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BeezeColors.PrimarySubtle else BeezeColors.SurfaceSoft)
                            .clickable { onSelect(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = BeezeType.bodySm.copy(fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal),
                            color = if (isSelected) BeezeColors.Primary else BeezeColors.Body,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
