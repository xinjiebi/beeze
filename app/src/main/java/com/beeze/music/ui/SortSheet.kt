package com.beeze.music.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.SongSortOption
import com.beeze.music.data.SongSortScope
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 歌曲排序底部面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(viewModel: PlayerViewModel, scope: SongSortScope, onDismiss: () -> Unit) {
    val options by viewModel.sortOptions.collectAsStateWithLifecycle()
    val selected = options[scope] ?: SongSortOption.Default

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BeezeColors.Surface,
        scrimColor = BeezeColors.Scrim,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(60.dp, 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BeezeColors.Muted)
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                tr(R.string.sort_by),
                style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                color = BeezeColors.Body,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SongSortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            viewModel.setSongSort(scope, option)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        sortOptionLabel(option),
                        style = BeezeType.bodyMd,
                        color = if (option == selected) BeezeColors.Primary else BeezeColors.Body,
                        modifier = Modifier.weight(1f),
                    )
                    if (option == selected) {
                        SortCheckmark()
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SortCheckmark() {
    val primary = BeezeColors.Primary
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        val path = Path().apply {
            moveTo(5f * unit, 12f * unit)
            lineTo(10f * unit, 17f * unit)
            lineTo(19f * unit, 7f * unit)
        }
        drawPath(
            path,
            primary,
            style = Stroke(
                width = 1.75f * unit,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
