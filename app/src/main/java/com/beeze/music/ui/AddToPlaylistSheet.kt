package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.Song
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 添加到歌单面板（Figma 87:1724）。 */
@Composable
fun AddToPlaylistSheet(viewModel: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    AddToPlaylistSheet(viewModel, listOf(song), onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(viewModel: PlayerViewModel, songs: List<Song>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()
    val songById = remember(allSongs) { allSongs.associateBy { it.id } }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                val playlist = viewModel.createPlaylistAndAdd(name, songs)
                val message = if (songs.size == 1) {
                    context.getString(R.string.added_one_to_new_playlist, playlist.name)
                } else {
                    context.getString(R.string.added_songs_to_new_playlist, songs.size, playlist.name)
                }
                showToast(context, message)
                showCreateDialog = false
                onDismiss()
            },
        )
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
        BoxWithConstraints {
            Column(Modifier.fillMaxWidth().height(357.dp.coerceAtMost(maxHeight))) {
                Text(
                    tr(R.string.add_to_playlist),
                    style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                    color = BeezeColors.Body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            PlaylistRow(
                                playlist = playlist,
                                firstSong = playlist.songIds.firstOrNull()?.let { songById[it] },
                                onClick = {
                                    val added = viewModel.addSongsToPlaylist(playlist.id, songs.map { it.id })
                                    val message = when {
                                        songs.size == 1 && added == 1 ->
                                            context.getString(R.string.added_to_playlist_named, playlist.name)
                                        added == 0 -> context.getString(R.string.already_in_playlist, playlist.name)
                                        added == songs.size ->
                                            context.getString(R.string.added_songs_to_playlist, added, playlist.name)
                                        else -> context.getString(R.string.added_songs_duplicates, added)
                                    }
                                    showToast(context, message)
                                    onDismiss()
                                },
                            )
                        }
                    }
                    item {
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            CreatePlaylistRow(onClick = { showCreateDialog = true })
                        }
                    }
                }
            }
        }
    }
}
