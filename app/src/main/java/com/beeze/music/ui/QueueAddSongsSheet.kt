package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 从媒体库多选歌曲并追加到当前播放队列。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueAddSongsSheet(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

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
                        .background(BeezeColors.Muted)
                )
            }
        },
    ) {
        BoxWithConstraints {
            Column(Modifier.fillMaxWidth().height((maxHeight * 0.75f).coerceAtLeast(320.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr(R.string.add_songs),
                        style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                        color = BeezeColors.Body,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (selectedIds.isEmpty()) tr(R.string.add) else tr(R.string.add_count, selectedIds.size),
                        style = BeezeType.button,
                        color = BeezeColors.Primary,
                        modifier = Modifier
                            .alpha(if (selectedIds.isEmpty()) 0.4f else 1f)
                            .then(
                                if (selectedIds.isEmpty()) Modifier
                                else Modifier.clickable {
                                    val selectedSongs = songs.filter { it.id in selectedIds }
                                    val added = viewModel.addSongsToQueue(selectedSongs)
                                    val message = if (added > 0) {
                                        context.getString(
                                            if (added == 1) R.string.added_one_to_queue else R.string.added_to_queue_count,
                                            added,
                                        )
                                    } else {
                                        context.getString(R.string.already_in_queue)
                                    }
                                    showToast(context, message)
                                    onDismiss()
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(songs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clickable {
                                    selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(song.artwork, 44.dp, RoundedCornerShape(8.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    song.title,
                                    style = BeezeType.button,
                                    color = BeezeColors.Body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    song.artist,
                                    style = BeezeType.caption,
                                    color = BeezeColors.Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            SelectionIndicator(song.id in selectedIds)
                        }
                    }
                }
            }
        }
    }
}
