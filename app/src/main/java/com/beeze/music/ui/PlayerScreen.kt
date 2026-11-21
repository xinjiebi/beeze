package com.beeze.music.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.beeze.music.PlayMode
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.LyricsRepository
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType
import com.beeze.music.ui.theme.LocalBeezeDarkTheme
import com.syncedlyrics.sdk.SyncedLyricsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 播放页始终使用歌词暗底配色，不随普通页面切换。 */
private val OnDark @Composable get() = BeezeColors.PlayerText
private val OnDarkDim @Composable get() = BeezeColors.PlayerTextDim
private val OnDarkFaint @Composable get() = BeezeColors.PlayerIconFaint
private val OnDarkTrack @Composable get() = BeezeColors.PlayerTrack
private val OnDarkAccent @Composable get() = BeezeColors.PlayerAccent

class PlayerContentCache {
    var loadedSongId: Long? = null
    var lyricsRevision: Long = -1L
    var lrc: String = ""
    var hasLyrics: Boolean = false
    var cover: Bitmap? = null
}

/**
 * 播放器页：SyncedLyrics 歌词动画作全屏背景层，Beeze 的封面/标题/进度条/控制叠加在上。
 * 整体暗色模式；有歌词时隐藏封面圆盘，让歌词动画占满视觉中心。
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    lyricView: SyncedLyricsView,
    contentCache: PlayerContentCache,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playMode by viewModel.playMode.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val lyricsFolders by viewModel.lyricsFolders.collectAsStateWithLifecycle()
    val lyricsRevision by viewModel.lyricsRevision.collectAsStateWithLifecycle()

    val song = currentSong ?: run {
        // 删除最后一首等导致队列为空时，不停留在空白播放页
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(BeezeColors.SurfaceSoft))
        return
    }
    val rotation by rememberCoverRotation(isPlaying)
    val context = LocalContext.current
    val lyricsFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.addLyricsFolder(it) { count ->
                showToast(context, context.getString(R.string.lyrics_discovery_result, count))
            }
        }
    }

    var lyricEffectsOpen by remember { mutableStateOf(false) }
    var sleepTimerOpen by remember { mutableStateOf(false) }
    var addPlaylistOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()

    // null = 歌词加载中，true = 有歌词（隐藏封面），false = 无歌词（显示封面）
    var hasLyrics by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(song.id, lyricsRevision) {
        if (contentCache.loadedSongId == song.id && contentCache.lyricsRevision == lyricsRevision) {
            hasLyrics = contentCache.hasLyrics
            lyricView.setPlaybackState(viewModel.positionMs.value, isPlaying)
            return@LaunchedEffect
        }
        hasLyrics = null
        lyricView.setLrc("")
        val lrc = withContext(Dispatchers.IO) { LyricsRepository.loadLrc(song) }.orEmpty()
        hasLyrics = lrc.isNotBlank()
        lyricView.setLrc(lrc)
        lyricView.setPlaybackState(viewModel.positionMs.value, isPlaying)

        val request = ImageRequest.Builder(context)
            .data(song.artwork)
            .allowHardware(false)
            .build()
        val result = Coil.imageLoader(context).execute(request)
        val cover = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        lyricView.setCover(cover)
        contentCache.loadedSongId = song.id
        contentCache.lyricsRevision = lyricsRevision
        contentCache.lrc = lrc
        contentCache.hasLyrics = hasLyrics == true
        contentCache.cover = cover
    }

    // SDK 在两次校准之间自行推算进度；播放中每 500ms 同步一次 ExoPlayer 的真实位置。
    LaunchedEffect(isPlaying, song.id) {
        lyricView.setPlaybackState(viewModel.positionMs.value, isPlaying)
        while (isPlaying) {
            delay(500)
            lyricView.setPlaybackState(viewModel.positionMs.value, true)
        }
    }

    // 播放页暗色背景：状态栏/手势条图标切浅色，离开恢复
    val darkTheme = LocalBeezeDarkTheme.current
    DisposableEffect(darkTheme) {
        val window = (context as? ComponentActivity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    Box(Modifier.fillMaxSize()) {
        // SyncedLyrics 原生歌词动画背景层
        AndroidView(factory = { lyricView }, modifier = Modifier.fillMaxSize())

        // 顶部压暗渐变：保证返回箭头和状态栏可读
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        0f to BeezeColors.PlayerShade.copy(alpha = 0.38f),
                        1f to BeezeColors.Transparent,
                    )
                )
        )

        // 底部渐变遮罩：让按钮和文字在歌词背景上看得清
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to BeezeColors.Transparent,
                        0.5f to BeezeColors.PlayerShade.copy(alpha = 0.30f),
                        1f to BeezeColors.PlayerShade.copy(alpha = 0.68f),
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶部只有一个向下箭头，点击返回主页
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_unfold),
                    contentDescription = tr(R.string.back),
                    colorFilter = ColorFilter.tint(OnDark),
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable(onClick = onBack),
                )
            }

            Spacer(Modifier.weight(65f))

            // 有歌词时不显示封面（歌词动画占视觉中心）；无歌词时显示 288 底圈 + 280 旋转封面
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(288.dp), contentAlignment = Alignment.Center) {
                    if (hasLyrics == false) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(BeezeColors.Body)
                        )
                        Surface(
                            modifier = Modifier
                                .size(280.dp)
                                .rotate(rotation),
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                        ) {
                            // 真实封面与默认封面都随播放旋转
                            CoverImage(
                                song.artwork,
                                280.dp,
                                CircleShape,
                                placeholder = { PlayerCoverPlaceholder(280.dp) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(80f))

            // 标题区（px 24）
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    song.title,
                    style = BeezeType.displayMd,
                    color = OnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = BeezeType.bodySm,
                    color = OnDarkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.weight(41f))

            // 功能行（px 16，justify-between，图标带 8dp 点击区域）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val liked = song.id in likedIds
                PlayerActionIcon(
                    iconRes = if (liked) R.drawable.ic_like_filled else R.drawable.ic_like,
                    label = tr(R.string.like),
                    tint = if (liked) OnDarkAccent else OnDark,
                    onClick = { viewModel.toggleLike(song.id) },
                )
                // 定时激活时图标位改为倒计时（HH:MM，默认图标色），点击仍可打开面板调整
                val timerRemaining = sleepTimerRemainingMs
                if (timerRemaining != null) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            // 定宽容纳 HH:MM:SS（8 字符），跨小时边界切换格式时布局不抖动
                            .width(72.dp)
                            .iconClickable { sleepTimerOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            formatSleepCountdown(timerRemaining),
                            style = BeezeType.button,
                            color = OnDark,
                        )
                    }
                } else {
                    PlayerActionIcon(R.drawable.ic_timer, tr(R.string.sleep_timer)) { sleepTimerOpen = true }
                }
                PlayerActionIcon(R.drawable.ic_add_playlist, tr(R.string.add_to_playlist)) { addPlaylistOpen = true }
                PlayerActionIcon(R.drawable.ic_lyrics, tr(R.string.lyric_effects)) {
                    if (lyricsFolders.isEmpty()) lyricsFolderLauncher.launch(null)
                    else lyricEffectsOpen = true
                }
                PlayerActionIcon(R.drawable.ic_player_more, tr(R.string.more)) { moreOpen = true }
            }

            Spacer(Modifier.weight(24f))

            // 横向进度条（px 20，支持点按与拖拽跳转；seek 同步给歌词层）
            ProgressSlider(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekFraction = { fraction ->
                    viewModel.seekToFraction(fraction)
                    if (durationMs > 0) {
                        lyricView.setPlaybackState((fraction * durationMs).toLong(), isPlaying)
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.weight(12f))

            // 传输控制行（px 24，justify-between）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 播放模式：列表循环 → 单曲循环 → 随机 循环切换（Figma 6:195/6:199/6:202）
                Image(
                    painter = painterResource(
                        when (playMode) {
                            PlayMode.RepeatAll -> R.drawable.ic_repeat
                            PlayMode.RepeatOne -> R.drawable.ic_repeat_one
                            PlayMode.Shuffle -> R.drawable.ic_shuffle
                        }
                    ),
                    contentDescription = when (playMode) {
                        PlayMode.RepeatAll -> tr(R.string.repeat_all)
                        PlayMode.RepeatOne -> tr(R.string.repeat_one)
                        PlayMode.Shuffle -> tr(R.string.shuffle)
                    },
                    // 三态仅切换图标，不做选中高亮
                    colorFilter = ColorFilter.tint(OnDark),
                    modifier = Modifier
                        .size(32.dp)
                        .iconClickable(onClick = viewModel::cyclePlayMode),
                )
                Image(
                    painter = painterResource(R.drawable.ic_prev),
                    contentDescription = tr(R.string.previous),
                    colorFilter = ColorFilter.tint(OnDark),
                    modifier = Modifier
                        .size(32.dp)
                        .iconClickable(onClick = viewModel::previous),
                )
                Image(
                    painter = painterResource(
                        if (isPlaying) R.drawable.ic_player_pause_on_dark
                        else R.drawable.ic_player_play_on_dark
                    ),
                    contentDescription = tr(if (isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier
                        .size(48.dp)
                        .iconClickable(onClick = viewModel::togglePlayPause),
                )
                Image(
                    painter = painterResource(R.drawable.ic_next),
                    contentDescription = tr(R.string.next),
                    colorFilter = ColorFilter.tint(OnDark),
                    modifier = Modifier
                        .size(32.dp)
                        .iconClickable(onClick = viewModel::next),
                )
                Image(
                    painter = painterResource(R.drawable.ic_playlist_player),
                    contentDescription = tr(R.string.queue),
                    colorFilter = ColorFilter.tint(OnDark),
                    modifier = Modifier
                        .size(32.dp)
                        .iconClickable(onClick = viewModel::openQueue),
                )
            }

            Spacer(Modifier.weight(40f))
        }
        if (lyricEffectsOpen) {
            LyricEffectsSheet(lyricView = lyricView, onDismiss = { lyricEffectsOpen = false })
        }
        if (sleepTimerOpen) {
            SleepTimerSheet(viewModel = viewModel, onDismiss = { sleepTimerOpen = false })
        }
        if (addPlaylistOpen) {
            AddToPlaylistSheet(
                viewModel = viewModel,
                song = song,
                onDismiss = { addPlaylistOpen = false },
            )
        }
        if (moreOpen) {
            SongActionsSheet(viewModel, song, onDismiss = { moreOpen = false })
        }
    }
}

/** 定时关闭倒计时：≤1 小时显示 MM:SS，>1 小时显示 HH:MM:SS（每秒读秒） */
private fun formatSleepCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    return if (totalSeconds <= 3600L) {
        "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    } else {
        "%02d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }
}

/** 底部功能图标：24dp 图标 + 8dp 点击区域（共 40dp 触控区），暗色模式下默认白色 */
@Composable
private fun PlayerActionIcon(
    iconRes: Int,
    label: String,
    tint: Color = OnDark,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .iconClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * 横向进度条（暗色模式）：2dp 轨道（25% 白）+ 已播放段（白），下方左右时间。
 * 支持点按和拖拽跳转；拖拽过程中显示预览位置与预览时间，松手才真正 seek。
 */
@Composable
private fun ProgressSlider(
    positionMs: Long,
    durationMs: Long,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackWidth by remember { mutableStateOf(1) }
    val playedFraction =
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // 拖拽中优先显示拖拽预览位置
    val fraction = dragFraction ?: playedFraction

    Column(
        modifier
            .fillMaxWidth()
            // 手势作用于整个进度条区块（轨道 + 时间行），可拖拽面积约 42dp 高
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeekFraction((offset.x / trackWidth).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / trackWidth).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let(onSeekFraction)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / trackWidth).coerceIn(0f, 1f)
                    },
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 视觉轨道只有 2dp，用内边距把轨道自身触控区域扩到 26dp 高
                .padding(vertical = 12.dp)
                .onSizeChanged { trackWidth = it.width.coerceAtLeast(1) },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 轨道
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(OnDarkTrack)
            )
            // 已播放段
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(OnDark)
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime((fraction * durationMs).toLong()),
                style = BeezeType.navLink,
                color = OnDarkDim,
            )
            Text(
                formatTime(durationMs),
                style = BeezeType.navLink,
                color = OnDarkDim,
            )
        }
    }
}
