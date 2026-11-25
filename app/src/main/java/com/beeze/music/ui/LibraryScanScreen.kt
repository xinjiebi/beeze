package com.beeze.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.ScanResult
import com.beeze.music.data.FilteredFolder
import com.beeze.music.data.Song
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType
import kotlin.math.roundToInt

/** 媒体库扫描进度、结果与过滤管理 */
@Composable
fun LibraryScanScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val manageOpen by viewModel.scanManageOpen.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val progress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val result by viewModel.scanResult.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val skipShortAudio by viewModel.skipShortAudio.collectAsStateWithLifecycle()
    val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val filteredFolders by viewModel.filteredFolders.collectAsStateWithLifecycle()

    BackHandler {
        if (manageOpen) viewModel.closeScanManage() else viewModel.closeScan()
    }

    Box(Modifier.fillMaxSize().background(BeezeColors.SurfaceSoft)) {
        Box(Modifier.fillMaxWidth().height(360.dp).topPurpleGradient())
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScanTopBar(
                title = tr(if (manageOpen) R.string.scan_manage else R.string.scan),
                showManage = !manageOpen,
                onBack = {
                    if (manageOpen) viewModel.closeScanManage() else viewModel.closeScan()
                },
                onManage = viewModel::openScanManage,
            )
            if (manageOpen) {
                ScanManagement(
                    skipShortAudio = skipShortAudio,
                    onSkipChanged = viewModel::setSkipShortAudio,
                    filteredSongs = filteredSongs,
                    filteredFolders = filteredFolders,
                    onRestoreSong = { song ->
                        viewModel.restoreFilteredSong(song)
                        showToast(context, context.getString(R.string.restored_item, song.title))
                    },
                    onRestoreFolder = { folder ->
                        viewModel.restoreFilteredFolder(folder)
                        showToast(context, context.getString(R.string.restored_item, folder.name))
                    },
                )
            } else {
                ScanContent(
                    scanning = scanning,
                    scanned = progress.scanned,
                    total = progress.total,
                    result = result,
                    hasError = scanError,
                    onRescan = viewModel::openScan,
                    onListen = viewModel::closeScan,
                )
            }
        }
    }
}

@Composable
private fun ScanTopBar(title: String, showManage: Boolean, onBack: () -> Unit, onManage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = tr(R.string.back),
            colorFilter = themeIconTint(),
            modifier = Modifier.size(32.dp).iconClickable(onClick = onBack).padding(4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = BeezeType.button, color = BeezeColors.Body, modifier = Modifier.weight(1f))
        if (showManage) {
            Text(
                tr(R.string.manage),
                style = BeezeType.button,
                color = BeezeColors.Body,
                modifier = Modifier.iconClickable(onClick = onManage),
            )
        }
    }
}

@Composable
private fun ScanContent(
    scanning: Boolean,
    scanned: Int,
    total: Int,
    result: ScanResult?,
    hasError: Boolean,
    onRescan: () -> Unit,
    onListen: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ScanVisualization(scanning)
        }
        when {
            hasError -> ScanError(onRescan)
            scanning || result == null -> ScanProgress(scanned, total)
            else -> ScanResultActions(result, onRescan, onListen)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            tr(R.string.scan_description),
            style = BeezeType.caption,
            color = BeezeColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun ScanError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tr(R.string.scan_failed), style = BeezeType.title, color = BeezeColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            tr(R.string.scan_failed_description),
            style = BeezeType.caption,
            color = BeezeColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ScanResultButton(tr(R.string.retry), onRetry, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ScanVisualization(scanning: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (scanning) {
            val transition = rememberInfiniteTransition(label = "scanBreathing")
            val outer by transition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "outerCircle",
            )
            val middle by transition.animateFloat(
                initialValue = 1.05f,
                targetValue = 0.94f,
                animationSpec = infiniteRepeatable(
                    tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "middleCircle",
            )
            val inner by transition.animateFloat(
                initialValue = 0.91f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    tween(1750, easing = FastOutSlowInEasing), RepeatMode.Reverse,
                ),
                label = "innerCircle",
            )
            ScanCircles(outer, middle, inner)
            ScanGatheringIcons(transition)
        } else {
            ScanCircles()
        }
    }
}

@Composable
private fun ScanCircles(outer: Float = 1f, middle: Float = 1f, inner: Float = 1f) {
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(300.dp).graphicsLayer { scaleX = outer; scaleY = outer }
                .clip(CircleShape).background(BeezeColors.Primary.copy(alpha = 0.2f)),
        )
        Box(
            Modifier.size(220.dp).graphicsLayer { scaleX = middle; scaleY = middle }
                .clip(CircleShape).background(BeezeColors.Primary.copy(alpha = 0.2f)),
        )
        Box(
            Modifier.size(120.dp).graphicsLayer { scaleX = inner; scaleY = inner }
                .clip(CircleShape).background(BeezeColors.Primary.copy(alpha = 0.2f)),
        )
    }
}

@Composable
private fun BoxScope.ScanGatheringIcons(transition: InfiniteTransition) {
    GatheringIcon(transition, R.drawable.ic_scan_note_small, 11.dp, 10.dp, 116.dp, (-180).dp, 0)
    GatheringIcon(transition, R.drawable.ic_scan_note, 22.dp, 20.dp, (-144).dp, (-129).dp, 430)
    GatheringIcon(transition, R.drawable.ic_scan_note, 22.dp, 20.dp, 94.dp, 195.dp, 1030)
    GatheringIcon(transition, R.drawable.ic_scan_star, 18.dp, 18.dp, (-129).dp, 159.dp, 1470)
    GatheringIcon(transition, R.drawable.ic_scan_star, 18.dp, 18.dp, 147.dp, (-84).dp, 1880)
}

@Composable
private fun BoxScope.GatheringIcon(
    transition: InfiniteTransition,
    iconRes: Int,
    width: Dp,
    height: Dp,
    startX: Dp,
    startY: Dp,
    delayMillis: Int,
) {
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "gatheringIcon",
    )
    val density = LocalDensity.current
    val x = with(density) { startX.toPx() }
    val y = with(density) { startY.toPx() }
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.align(Alignment.Center).size(width, height).graphicsLayer {
            translationX = x * (1f - progress)
            translationY = y * (1f - progress)
            scaleX = 1f - progress * 0.5f
            scaleY = scaleX
            alpha = when {
                progress < 0.12f -> progress / 0.12f
                progress > 0.78f -> (1f - progress) / 0.22f
                else -> 1f
            }.coerceIn(0f, 1f)
        },
    )
}

@Composable
internal fun ScanProgress(scanned: Int, total: Int) {
    val target = if (total > 0) scanned.toFloat() / total else 0f
    val animated by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(180, easing = LinearEasing),
        label = "scanProgress",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${(animated * 100).roundToInt()}",
                color = BeezeColors.Primary,
                fontSize = 34.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            Text(
                "%",
                color = BeezeColors.Primary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.2.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(tr(R.string.scanning), style = BeezeType.caption, color = BeezeColors.Muted)
    }
}

@Composable
private fun ScanResultActions(result: ScanResult, onRescan: () -> Unit, onListen: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScanResultButton(tr(R.string.rescan), onRescan, Modifier.weight(1f))
            ScanResultButton(tr(R.string.listen), onListen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            tr(if (result.addedSongs == 1) R.string.new_song_count else R.string.new_songs_count, result.addedSongs),
            style = BeezeType.caption,
            color = BeezeColors.Body,
        )
    }
}

@Composable
private fun ScanResultButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BeezeColors.Primary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BeezeColors.Primary),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(label, style = BeezeType.button, maxLines = 1)
    }
}

@Composable
private fun ScanManagement(
    skipShortAudio: Boolean,
    onSkipChanged: (Boolean) -> Unit,
    filteredSongs: List<Song>,
    filteredFolders: List<FilteredFolder>,
    onRestoreSong: (Song) -> Unit,
    onRestoreFolder: (FilteredFolder) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp)).background(BeezeColors.Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr(R.string.skip_audio_60), style = BeezeType.button, color = BeezeColors.Ink, modifier = Modifier.weight(1f))
                ScanSwitch(skipShortAudio, onSkipChanged)
            }
        }
        if (filteredSongs.isNotEmpty() || filteredFolders.isNotEmpty()) {
            item {
                Text(
                    tr(R.string.filter_delete_explanation),
                    style = BeezeType.caption,
                    color = BeezeColors.Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        if (filteredSongs.isNotEmpty()) {
            item { FilterSectionTitle(tr(R.string.filtered_songs)) }
            items(filteredSongs, key = { it.path }) { song ->
                FilteredSongRow(song, onRestore = { onRestoreSong(song) })
            }
        }
        if (filteredFolders.isNotEmpty()) {
            item { FilterSectionTitle(tr(R.string.filtered_folders)) }
            items(filteredFolders, key = { it.path }) { folder ->
                FilteredFolderRow(folder, onRestore = { onRestoreFolder(folder) })
            }
        }
    }
}

@Composable
private fun ScanSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val description = tr(R.string.skip_audio_description)
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 14f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "scanSwitch",
    )
    Box(
        modifier = Modifier.size(38.dp, 24.dp).clip(RoundedCornerShape(12.dp))
            .background(if (checked) BeezeColors.Primary else BeezeColors.Hairline)
            .semantics { contentDescription = description }
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = 2.dp).graphicsLayer { translationX = thumbOffset * density }
                .size(20.dp).clip(CircleShape).background(BeezeColors.Surface),
        )
    }
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        title,
        style = BeezeType.bodySm,
        color = BeezeColors.Muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun FilteredSongRow(song: Song, onRestore: () -> Unit) {
    FilterRow(
        title = song.title,
        subtitle = song.artist,
        onRestore = onRestore,
        leading = { CoverImage(song.artwork, 44.dp, RoundedCornerShape(8.dp)) },
    )
}

@Composable
private fun FilteredFolderRow(folder: FilteredFolder, onRestore: () -> Unit) {
    FilterRow(
        title = folder.name,
        subtitle = "${songsCount(folder.songCount)} · ${folder.path}",
        onRestore = onRestore,
        leading = {
            Image(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                colorFilter = themeIconTint(BeezeColors.PrimarySubtle),
                modifier = Modifier.size(44.dp),
            )
        },
    )
}

@Composable
private fun FilterRow(title: String, subtitle: String, onRestore: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BeezeType.button, color = BeezeColors.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = BeezeType.caption, color = BeezeColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            tr(R.string.restore),
            style = BeezeType.caption,
            color = BeezeColors.Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(onClick = onRestore),
        )
    }
}
