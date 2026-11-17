package com.beeze.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.Song
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType
import kotlin.math.roundToInt

/** 歌曲批量选择（Figma 179:4905 / 180:5568 / 180:5788） */
@Composable
fun BatchSelectionScreen(
    viewModel: PlayerViewModel,
    songs: List<Song>,
    playlistId: Long?,
    onCancel: () -> Unit,
    onDone: (List<Song>) -> Unit,
) {
    val context = LocalContext.current
    var orderedSongs by remember(songs) { mutableStateOf(songs) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectedSongs = orderedSongs.filter { it.id in selectedIds }
    BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize().background(BeezeColors.SurfaceSoft)) {
        Box(Modifier.fillMaxWidth().height(360.dp).topPurpleGradient())
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Cancel",
                    style = BeezeType.button,
                    color = BeezeColors.Muted,
                    modifier = Modifier.width(60.dp).clickable(onClick = onCancel),
                )
                Text(
                    if (selectedIds.isEmpty()) "" else "Selected(${selectedIds.size})",
                    style = BeezeType.button,
                    color = BeezeColors.Body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Done",
                    style = BeezeType.button,
                    color = BeezeColors.Primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(60.dp).clickable { onDone(orderedSongs) },
                )
            }

            val listState = rememberLazyListState()
            val rowHeightPx = with(LocalDensity.current) { 60.dp.toPx() }
            var dragFrom by remember(songs) { mutableIntStateOf(-1) }
            var dragOffset by remember(songs) { mutableFloatStateOf(0f) }
            val dragTarget = if (dragFrom >= 0) {
                (dragFrom + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, orderedSongs.lastIndex)
            } else {
                -1
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(orderedSongs, key = { _, song -> song.id }) { index, song ->
                    val shiftPx = when {
                        dragFrom < 0 || index == dragFrom -> 0f
                        dragTarget > dragFrom && index in (dragFrom + 1)..dragTarget -> -rowHeightPx
                        dragTarget in 0..<dragFrom && index in dragTarget..<dragFrom -> rowHeightPx
                        else -> 0f
                    }
                    BatchSongRow(
                        song = song,
                        selected = song.id in selectedIds,
                        showDragHandle = playlistId != null,
                        onClick = {
                            selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                        },
                        modifier = Modifier
                            .zIndex(if (index == dragFrom) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (index == dragFrom) dragOffset else shiftPx
                            },
                        dragHandle = if (playlistId == null) {
                            Modifier
                        } else {
                            Modifier.playlistDragHandle(
                                onDragStart = {
                                    dragFrom = index
                                    dragOffset = 0f
                                },
                                onDrag = { delta -> dragOffset += delta },
                                onDragEnd = {
                                    val target = if (dragFrom >= 0) {
                                        (dragFrom + (dragOffset / rowHeightPx).roundToInt())
                                            .coerceIn(0, orderedSongs.lastIndex)
                                    } else {
                                        -1
                                    }
                                    if (dragFrom >= 0 && target >= 0 && target != dragFrom) {
                                        val reordered = orderedSongs.toMutableList()
                                        val moved = reordered.removeAt(dragFrom)
                                        reordered.add(target, moved)
                                        orderedSongs = reordered
                                    }
                                    dragFrom = -1
                                    dragOffset = 0f
                                },
                            )
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BeezeColors.SurfaceSoft)
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val enabled = selectedIds.isNotEmpty()
                BatchAction(
                    iconRes = R.drawable.ic_like,
                    label = "Liked",
                    width = 48.dp,
                    enabled = enabled,
                    onClick = {
                        val added = viewModel.likeSongs(selectedIds)
                        showToast(context, if (added > 0) "Added $added song${if (added > 1) "s" else ""} to Liked" else "Already in Liked")
                    },
                )
                BatchAction(
                    iconRes = R.drawable.ic_add_playlist,
                    label = "Add to Playlist",
                    width = 96.dp,
                    enabled = enabled,
                    onClick = { showAddToPlaylist = true },
                )
                BatchAction(
                    iconRes = R.drawable.ic_queue_clear,
                    label = "Delete",
                    width = 48.dp,
                    enabled = enabled,
                    onClick = {
                        if (playlistId == null) {
                            showDeleteDialog = true
                        } else {
                            viewModel.removeSongsFromPlaylist(playlistId, selectedIds)
                            showToast(context, "Removed ${selectedIds.size} song${if (selectedIds.size > 1) "s" else ""} from playlist")
                            onCancel()
                        }
                    },
                )
            }
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistSheet(
            viewModel = viewModel,
            songs = selectedSongs,
            onDismiss = { showAddToPlaylist = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = BeezeColors.Surface,
            title = { Text("Delete from Beeze?", style = BeezeType.title, color = BeezeColors.Body) },
            text = {
                Text(
                    "This removes ${selectedIds.size} selected song${if (selectedIds.size > 1) "s" else ""} from Beeze, not from your device. Future scans will filter them. You can restore them in Library scan.",
                    style = BeezeType.bodySm,
                    color = BeezeColors.Muted,
                )
            },
            confirmButton = {
                Text(
                    "Delete",
                    style = BeezeType.button,
                    color = BeezeColors.Primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable {
                        viewModel.deleteSongsFromApp(selectedSongs)
                        showToast(context, "Deleted from Beeze")
                        showDeleteDialog = false
                        onCancel()
                    },
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
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

private fun Modifier.playlistDragHandle(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { onDragStart() },
        onDrag = { change, amount ->
            change.consume()
            onDrag(amount.y)
        },
        onDragEnd = onDragEnd,
        onDragCancel = onDragEnd,
    )
}

@Composable
private fun BatchSongRow(
    song: Song,
    selected: Boolean,
    showDragHandle: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDragHandle) {
            Image(
                painter = painterResource(R.drawable.ic_queue_move),
                contentDescription = "拖拽排序",
                modifier = Modifier.size(24.dp).then(dragHandle),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Spacer(Modifier.width(8.dp))
        SelectionIndicator(selected)
    }
}

@Composable
internal fun SelectionIndicator(selected: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        if (selected) {
            drawCircle(BeezeColors.Primary, radius = 9f * unit)
            val check = Path().apply {
                moveTo(8f * unit, 12f * unit)
                lineTo(11f * unit, 15f * unit)
                lineTo(17f * unit, 9f * unit)
            }
            drawPath(
                check,
                Color.White,
                style = Stroke(
                    width = 1.75f * unit,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        } else {
            drawCircle(
                BeezeColors.Muted,
                radius = 8.125f * unit,
                style = Stroke(width = 1.75f * unit),
            )
        }
    }
}

@Composable
private fun BatchAction(
    iconRes: Int,
    label: String,
    width: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (enabled) Modifier.iconClickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )
        Text(
            label,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = BeezeColors.Muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
