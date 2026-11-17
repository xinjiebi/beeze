package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.Song
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 文件夹、专辑、艺术家等歌曲集合的批量操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionActionsSheet(
    viewModel: PlayerViewModel,
    title: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showAddToPlaylist by remember { mutableStateOf(false) }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(viewModel, songs, onDismiss = onDismiss)
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BeezeColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
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
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    title,
                    style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                    color = BeezeColors.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${songs.size} song${if (songs.size > 1) "s" else ""}",
                    style = BeezeType.caption,
                    color = BeezeColors.Muted,
                )
            }
            SongActionRow(
                iconRes = R.drawable.ic_play_all,
                label = "Play",
                onClick = {
                    viewModel.playSongs(songs, 0)
                    onDismiss()
                },
            )
            SongActionRow(
                iconRes = R.drawable.ic_shuffle,
                label = "Shuffle",
                onClick = {
                    viewModel.playShuffled(songs)
                    onDismiss()
                },
            )
            SongActionRow(
                iconRes = R.drawable.ic_queue_add,
                label = "Add to Queue",
                onClick = {
                    val added = viewModel.addSongsToQueue(songs)
                    showToast(
                        context,
                        if (added > 0) "Added $added song${if (added > 1) "s" else ""} to queue"
                        else "Already in queue",
                    )
                    onDismiss()
                },
            )
            SongActionRow(
                iconRes = R.drawable.ic_add_playlist,
                label = "Add to Playlist",
                onClick = { showAddToPlaylist = true },
            )
        }
    }
}
