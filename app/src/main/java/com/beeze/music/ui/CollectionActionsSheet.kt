package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(viewModel, songs, onDismiss = onDismiss)
        return
    }

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
                    songsCount(songs.size),
                    style = BeezeType.caption,
                    color = BeezeColors.Muted,
                )
            }
            SongActionRow(
                iconRes = R.drawable.ic_play_all,
                label = tr(R.string.play),
                onClick = {
                    viewModel.playSongs(songs, 0)
                    onDismiss()
                },
            )
            SongActionRow(
                iconRes = R.drawable.ic_shuffle,
                label = tr(R.string.shuffle),
                onClick = {
                    viewModel.playShuffled(songs)
                    onDismiss()
                },
            )
            SongActionRow(
                iconRes = R.drawable.ic_queue_add,
                label = tr(R.string.add_to_queue),
                onClick = {
                    val added = viewModel.addSongsToQueue(songs)
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
                },
            )
            SongActionRow(
                iconRes = R.drawable.ic_add_playlist,
                label = tr(R.string.add_to_playlist),
                onClick = { showAddToPlaylist = true },
            )
            if (onDelete != null) {
                SongActionRow(
                    iconRes = R.drawable.ic_queue_clear,
                    label = tr(R.string.delete),
                    onClick = { showDeleteDialog = true },
                )
            }
        }
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = BeezeColors.Surface,
            title = { Text(tr(R.string.delete_folder_from_beeze), style = BeezeType.title, color = BeezeColors.Body) },
            text = {
                Text(
                    tr(R.string.delete_folder_explanation),
                    style = BeezeType.bodySm,
                    color = BeezeColors.Muted,
                )
            },
            confirmButton = {
                Text(
                    tr(R.string.delete),
                    style = BeezeType.button,
                    color = BeezeColors.Primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable {
                        onDelete()
                        showToast(context, context.getString(R.string.folder_deleted_from_beeze))
                        showDeleteDialog = false
                        onDismiss()
                    },
                )
            },
            dismissButton = {
                Text(
                    tr(R.string.cancel),
                    style = BeezeType.button,
                    color = BeezeColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable {
                        showDeleteDialog = false
                    },
                )
            },
        )
    }
}
