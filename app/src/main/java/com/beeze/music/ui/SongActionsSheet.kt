package com.beeze.music.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.Song
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 单曲操作面板：加入歌单、喜欢、重命名、歌曲信息与删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    viewModel: PlayerViewModel,
    song: Song,
    playlistId: Long? = null,
    queueIndex: Int? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val isLiked = song.id in likedIds
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(viewModel, song, onDismiss = onDismiss)
        return
    }

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
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    song.title,
                    style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
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
            SongActionRow(
                iconRes = R.drawable.ic_add_playlist,
                label = tr(R.string.add_to_playlist),
                onClick = { showAddToPlaylist = true },
            )
            SongActionRow(
                iconRes = if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like,
                label = tr(if (isLiked) R.string.remove_from_liked else R.string.add_to_liked),
                onClick = {
                    viewModel.toggleLike(song.id)
                    showToast(
                        context,
                        context.getString(if (isLiked) R.string.removed_from_liked else R.string.added_to_liked),
                    )
                    onDismiss()
                },
            )
            SongActionRow(
                iconRes = R.drawable.ic_edit,
                label = tr(R.string.rename_in_beeze),
                onClick = { showRenameDialog = true },
            )
            SongActionRow(
                iconRes = R.drawable.ic_info,
                label = tr(R.string.song_info),
                onClick = { showInfoDialog = true },
            )
            SongActionRow(
                iconRes = R.drawable.ic_queue_clear,
                label = when {
                    queueIndex != null -> tr(R.string.remove_from_queue)
                    playlistId != null -> tr(R.string.remove_from_playlist)
                    else -> tr(R.string.delete)
                },
                onClick = {
                    when {
                        queueIndex != null -> {
                            viewModel.removeQueueItem(queueIndex)
                            showToast(context, context.getString(R.string.removed_from_queue))
                            onDismiss()
                        }
                        playlistId != null -> {
                            viewModel.removeSongsFromPlaylist(playlistId, setOf(song.id))
                            showToast(context, context.getString(R.string.removed_from_playlist))
                            onDismiss()
                        }
                        else -> showDeleteDialog = true
                    }
                },
            )
        }
    }

    if (showRenameDialog) {
        RenameSongDialog(
            viewModel,
            song,
            onDismiss = {
                showRenameDialog = false
                onDismiss()
            },
        )
    }
    if (showInfoDialog) {
        SongInfoDialog(viewModel, song, onDismiss = { showInfoDialog = false })
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = BeezeColors.Surface,
            title = { Text(tr(R.string.delete_from_beeze), style = BeezeType.title, color = BeezeColors.Body) },
            text = {
                Text(
                    tr(R.string.delete_song_explanation),
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
                        viewModel.deleteSongsFromApp(listOf(song))
                        showToast(context, context.getString(R.string.deleted_from_beeze))
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

@Composable
internal fun SongActionRow(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            colorFilter = themeIconTint(
                if (iconRes == R.drawable.ic_play_all || iconRes == R.drawable.ic_like_filled) {
                    BeezeColors.Primary
                } else {
                    BeezeColors.Body
                }
            ),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = BeezeType.bodyMd, color = BeezeColors.Body)
    }
}
