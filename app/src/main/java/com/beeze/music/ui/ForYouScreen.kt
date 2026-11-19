package com.beeze.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.AppScreen
import com.beeze.music.DetailPage
import com.beeze.music.EntryKind
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.ForYouCovers
import com.beeze.music.data.Playlist
import com.beeze.music.data.Song
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/**
 * For you 页（Figma 161:3757 / 162:5769 / 162:4964）：
 * 快捷卡片（Lyric Sync / Liked / Most Played / Shuffle Play，按数据显隐）
 * + Recently played（超过 3 首才显示）+ Featured 精选 9 首 + Playlist。
 */
@Composable
internal fun ForYouContent(viewModel: PlayerViewModel, isPageSettled: Boolean) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val lyricSongs by viewModel.lyricSongs.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val featuredSongs by viewModel.featuredSongs.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val recentSongs by viewModel.recentSongs.collectAsStateWithLifecycle()
    val covers by viewModel.cardCovers.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val isActivePage = isPageSettled && screen == AppScreen.Home && !searchOpen

    // Featured 刷新由 ViewModel 在冷启动媒体库加载时触发，进页不刷新

    val recentlyPlayedTitle = tr(R.string.recently_played)
    val openSongList: (String, List<Song>) -> Unit = { title, list ->
        if (list.isEmpty()) showToast(context, context.getString(R.string.no_content_yet))
        else viewModel.openDetail(DetailPage.SongList(title, list.map { it.id }, sortable = false))
    }
    val openEntry: (EntryKind, List<Song>) -> Unit = { kind, list ->
        viewModel.openDetail(DetailPage.EntryList(kind, list.map { it.id }))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp), // 给 MiniPlayer 留位
        verticalArrangement = Arrangement.spacedBy(12.dp), // 各区块间距 12（Figma 162:4644）
    ) {
        if (songs.isNotEmpty()) {
            CardsRow(
                lyricSongs = lyricSongs,
                likedSongs = likedSongs,
                mostPlayedSongs = mostPlayedSongs,
                shuffleCount = songs.size,
                covers = covers,
                isActivePage = isActivePage,
                onOpenEntry = openEntry,
                onShuffle = { viewModel.playShuffled(songs) },
            )
        }
        // 最近播放超过 3 首才显示
        if (recentSongs.size > 3) {
            RecentlyPlayedSection(
                songs = recentSongs,
                isActivePage = isActivePage,
                onPlay = { viewModel.playSongs(recentSongs, it, openPlayer = true) },
                onPlayAll = { viewModel.playSongs(recentSongs, 0) },
                onOpenAll = { openSongList(recentlyPlayedTitle, recentSongs) },
            )
        }
        if (featuredSongs.isNotEmpty()) {
            FeaturedSection(
                songs = featuredSongs,
                onPlay = { viewModel.playSongs(featuredSongs, it, openPlayer = true) },
                onPlayAll = { viewModel.playSongs(featuredSongs, 0) },
            )
        }
        PlaylistSection(
            playlists = playlists,
            songs = songs,
            onCreate = viewModel::createPlaylist,
            onOpen = { playlist ->
                viewModel.openDetail(
                    DetailPage.SongList(playlist.name, playlist.songIds, playlistId = playlist.id)
                )
            },
        )
        if (songs.isEmpty()) {
            Spacer(Modifier.height(48.dp))
            Text(
                tr(R.string.no_audio_files),
                style = BeezeType.bodyMd,
                color = BeezeColors.Muted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ---------- 顶部快捷卡片（Figma 162:5769） ----------

@Composable
private fun CardsRow(
    lyricSongs: List<Song>,
    likedSongs: List<Song>,
    mostPlayedSongs: List<Song>,
    shuffleCount: Int,
    covers: ForYouCovers,
    isActivePage: Boolean,
    onOpenEntry: (EntryKind, List<Song>) -> Unit,
    onShuffle: () -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(isActivePage) {
        if (!isActivePage) scrollState.scrollTo(0)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp), // 标签行与卡片区间距 8（内容区 top 148）
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 显隐与顺序：Lyric Sync（无歌词歌曲则不显示）→ Liked（恒显示，空数据也可进入）
        // → Shuffle Play（恒显示，直接随机播放全部）→ Most Played（无播放数据不显示）
        if (lyricSongs.isNotEmpty()) {
            val labelSong = covers.lyric ?: lyricSongs.first()
            EntryCard(
                width = 183.dp,
                gradient = listOf(BeezeColors.LyricCardStart, BeezeColors.LyricCardEnd),
                gradientStart = Offset(0f, 1f), // 132deg：左下青绿 → 右上紫
                gradientEnd = Offset(1f, 0f),
                line1 = "${labelSong.title} - ${labelSong.artist}",
                songCount = lyricSongs.size,
                cover = covers.lyric,
                onClick = { onOpenEntry(EntryKind.LyricSync, lyricSongs) },
                topLabel = { LyricSyncTopLabel() },
            )
        }
        EntryCard(
            width = 128.dp,
            gradient = listOf(BeezeColors.LikedCardStart, BeezeColors.LikedCardEnd),
            gradientStart = Offset.Zero, // 55deg：左上品红 → 右下黄
            gradientEnd = Offset(1f, 1f),
            line1 = tr(R.string.entry_liked),
            songCount = likedSongs.size,
            cover = covers.liked,
            onClick = { onOpenEntry(EntryKind.Liked, likedSongs) },
            defaultCover = { LikedDefaultCover() },
        )
        EntryCard(
            width = 128.dp,
            gradient = listOf(BeezeColors.ShuffleCardStart, BeezeColors.ShuffleCardEnd),
            gradientStart = Offset.Zero, // 52deg：左上紫 → 右下蓝
            gradientEnd = Offset(1f, 1f),
            line1 = tr(R.string.shuffle_play),
            songCount = shuffleCount,
            cover = covers.shuffle,
            onClick = onShuffle,
        )
        if (mostPlayedSongs.isNotEmpty()) {
            EntryCard(
                width = 128.dp,
                gradient = listOf(BeezeColors.MostPlayedCardStart, BeezeColors.MostPlayedCardEnd),
                gradientStart = Offset.Zero, // 51deg：左上绿 → 右下蓝
                gradientEnd = Offset(1f, 1f),
                line1 = tr(R.string.entry_most_played),
                songCount = mostPlayedSongs.size,
                cover = covers.mostPlayed,
                onClick = { onOpenEntry(EntryKind.MostPlayed, mostPlayedSongs) },
            )
        }
    }
}

/**
 * 入口卡片：渐变底 + 右上角封面（右缘露黑胶盘）+ 底部两行文案 + 播放胶囊。
 * 高度 128，圆角 12；封面 68x68 距右 16 距顶 11，黑胶盘距右 4。
 */
@Composable
private fun EntryCard(
    width: Dp,
    gradient: List<Color>,
    gradientStart: Offset,
    gradientEnd: Offset,
    line1: String,
    songCount: Int,
    cover: Song?,
    onClick: () -> Unit,
    topLabel: (@Composable () -> Unit)? = null,
    defaultCover: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        Modifier
            .size(width, 128.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    gradient,
                    start = Offset(size.width * gradientStart.x, size.height * gradientStart.y),
                    end = Offset(size.width * gradientEnd.x, size.height * gradientEnd.y),
                )
            )
        }
        topLabel?.invoke()
        // 黑胶盘：封面右后方露出一条边
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 11.dp, end = 4.dp)
                .size(68.dp),
        ) {
            CdDisc()
        }
        if (cover != null) {
            CoverImage(
                cover.artwork,
                68.dp,
                RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 11.dp, end = 16.dp),
                placeholder = {
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BeezeColors.OnGradient.copy(alpha = 0.2f))
                    )
                },
            )
        } else {
            // 无封面时的默认封面（如 Liked 空数据的渐变爱心封面）
            defaultCover?.invoke(this)
        }
        // 底部文案 + 播放胶囊
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    line1,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    letterSpacing = 0.5.sp,
                    color = BeezeColors.OnGradient,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    songsCount(songCount),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.3.sp,
                    color = BeezeColors.OnGradient.copy(alpha = 0.65f),
                    maxLines = 1,
                )
            }
            WhitePlayPill()
        }
    }
}

/** Lyric Sync 卡片左上角的两行标题：第二行缩进（Figma 161:4210） */
@Composable
private fun LyricSyncTopLabel() {
    Column(Modifier.padding(start = 8.dp, top = 11.dp)) {
        Text(
            tr(R.string.lyric),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.4.sp,
            color = BeezeColors.OnGradient.copy(alpha = 0.6f),
        )
        Text(
            tr(R.string.sync),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.4.sp,
            color = BeezeColors.OnGradient.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 28.dp),
        )
    }
}

/** Liked 空数据的默认封面：粉→浅橙渐变方块（圆角 4）+ 居中白色爱心（Figma 168:9372） */
@Composable
private fun BoxScope.LikedDefaultCover() {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = 11.dp, end = 16.dp)
            .size(68.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(BeezeColors.LikedCoverStart, BeezeColors.LikedCoverEnd),
                    start = Offset.Zero,
                    end = Offset.Infinite, // 142deg：左上粉 → 右下浅橙
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 设计稿的爱心不是实心白：50% 白 → 全透明 的斜向渐变（Figma Union，viewBox 36x33）
        Canvas(Modifier.size(36.dp, 33.dp)) {
            val sx = size.width / 36f
            val sy = size.height / 33f
            fun x(v: Float) = v * sx
            fun y(v: Float) = v * sy
            val heart = Path().apply {
                moveTo(x(32.9121f), y(3.09637f))
                cubicTo(x(28.7949f), y(-1.03212f), x(22.1196f), y(-1.03212f), x(18.0024f), y(3.09637f))
                lineTo(x(18f), y(3.09877f))
                lineTo(x(17.9976f), y(3.09637f))
                cubicTo(x(13.8804f), y(-1.03212f), x(7.2051f), y(-1.03212f), x(3.0879f), y(3.09637f))
                cubicTo(x(-1.0293f), y(7.22487f), x(-1.0293f), y(13.9185f), x(3.0879f), y(18.047f))
                lineTo(x(18f), y(33f))
                lineTo(x(32.9121f), y(18.047f))
                cubicTo(x(37.0293f), y(13.9185f), x(37.0293f), y(7.22487f), x(32.9121f), y(3.09637f))
                close()
            }
            drawPath(
                heart,
                Brush.linearGradient(
                    0f to BeezeColors.OnGradient.copy(alpha = 0.5f),
                    1f to BeezeColors.OnGradient.copy(alpha = 0f),
                    start = Offset(x(33f), y(-2f)),
                    end = Offset(x(4.5f), y(33f)),
                )
            )
        }
    }
}

/** 黑胶盘装饰：70% 透明深色圆盘 + 内圈纹路 + 中心孔 */
@Composable
private fun CdDisc() {
    Canvas(Modifier.fillMaxSize()) {
        val color = BeezeColors.Disc.copy(alpha = 0.7f)
        val radius = size.minDimension / 2f
        drawCircle(color, radius)
        // 纹路圈
        drawCircle(
            BeezeColors.OnGradient.copy(alpha = 0.15f),
            radius * 0.72f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
        )
        drawCircle(
            BeezeColors.OnGradient.copy(alpha = 0.15f),
            radius * 0.5f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
        )
        // 中心孔
        drawCircle(BeezeColors.OnGradient.copy(alpha = 0.35f), radius * 0.14f)
    }
}

/** 卡片右下角的播放胶囊：24x18 白色 20% 透明底 + 白色 85% 三角（Figma Frame 2147229915） */
@Composable
private fun WhitePlayPill() {
    Box(
        Modifier
            .size(24.dp, 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(BeezeColors.OnGradient.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPath(playTriangle(size), BeezeColors.OnGradient.copy(alpha = 0.85f))
        }
    }
}

// ---------- 分区标题 ----------

/**
 * 分区标题行：16sp 加粗标题（可带黑色小播放钮）+ 右侧操作图标。
 * pt 16 / px 20，底部不留 padding——与下方内容的间距由内容自身的 pt/py 8 统一承担。
 */
@Composable
private fun SectionTitle(
    title: String,
    showPlayPill: Boolean = false,
    onPlayClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
            color = BeezeColors.Body,
        )
        if (showPlayPill) {
            Spacer(Modifier.width(4.dp))
            BlackPlayPill(width = 24.dp, height = 18.dp, onClick = onPlayClick)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/** 黑色圆角胶囊播放钮（Recently played / Featured 标题用） */
@Composable
private fun BlackPlayPill(width: Dp, height: Dp, onClick: (() -> Unit)?) {
    val iconColor = BeezeColors.SectionPlayIcon
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(height / 2))
            .background(BeezeColors.SectionPlayBackground)
            .then(if (onClick != null) Modifier.iconClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawPath(playTriangle(size), iconColor) }
    }
}

/** 圆角播放三角形：与 Figma Vector 14 一致的路径（viewBox 24x18，圆角由贝塞尔曲线形成） */
private fun playTriangle(size: androidx.compose.ui.geometry.Size): Path {
    val sx = size.width / 24f
    val sy = size.height / 18f
    fun x(v: Float) = v * sx
    fun y(v: Float) = v * sy
    return Path().apply {
        moveTo(x(9f), y(11.938f))
        lineTo(x(9f), y(6.062f))
        cubicTo(x(9f), y(5.16436f), x(9.98737f), y(4.61711f), x(10.7486f), y(5.09286f))
        lineTo(x(15.837f), y(8.27314f))
        cubicTo(x(16.3742f), y(8.60886f), x(16.3742f), y(9.39114f), x(15.837f), y(9.72686f))
        lineTo(x(10.7486f), y(12.9071f))
        cubicTo(x(9.98737f), y(13.3829f), x(9f), y(12.8356f), x(9f), y(11.938f))
        close()
    }
}

// ---------- Recently played ----------

@Composable
private fun RecentlyPlayedSection(
    songs: List<Song>,
    isActivePage: Boolean,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onOpenAll: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(isActivePage) {
        if (!isActivePage) listState.scrollToItem(0)
    }
    LaunchedEffect(songs.firstOrNull()?.id) {
        if (isActivePage) listState.scrollToItem(0)
    }
    SectionTitle(
        title = tr(R.string.recently_played),
        showPlayPill = true,
        onPlayClick = onPlayAll,
        action = {
            Image(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = tr(R.string.view_all),
                colorFilter = themeIconTint(),
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onOpenAll),
            )
        },
    )
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            Column(
                modifier = Modifier
                    .width(101.dp)
                    .clickable { onPlay(index) },
            ) {
                CoverImage(song.artwork, 101.dp, RoundedCornerShape(8.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    song.title,
                    style = BeezeType.caption.copy(fontWeight = FontWeight.Medium),
                    color = BeezeColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = BeezeColors.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------- Featured（精选 9 首，按列翻页吸附） ----------

@Composable
private fun FeaturedSection(songs: List<Song>, onPlay: (Int) -> Unit, onPlayAll: () -> Unit) {
    SectionTitle(
        title = tr(R.string.featured),
        showPlayPill = true,
        onPlayClick = onPlayAll,
    )
    val columns = remember(songs) { songs.chunked(3) }
    // 每列 265 宽：翻页吸附后下一列左缘正好落在 20dp 的内容 padding 处。
    // 尾部留出「屏宽 - 20(左padding) - 265(列宽)」的空间，
    // 否则末页被最大滚动量钳制，最后一列永远对不齐左侧
    val endPadding = (LocalConfiguration.current.screenWidthDp.dp - 285.dp).coerceAtLeast(20.dp)
    HorizontalPager(
        state = rememberPagerState(pageCount = { columns.size }),
        pageSize = PageSize.Fixed(265.dp),
        contentPadding = PaddingValues(start = 20.dp, end = endPadding, top = 2.dp),
        pageSpacing = 8.dp,
        modifier = Modifier.height(180.dp), // 3 行 × (44 封面 + 16 行距)
    ) { page ->
        Column {
            columns[page].forEachIndexed { rowIndex, song ->
                FeaturedSongRow(
                    song = song,
                    onClick = { onPlay(page * 3 + rowIndex) },
                )
            }
        }
    }
}

/** Featured 歌曲行：44 封面 + 标题/艺术家，py 8（Figma songlist） */
@Composable
private fun FeaturedSongRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverImage(song.artwork, 44.dp, RoundedCornerShape(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    }
}

// ---------- Playlist ----------

@Composable
private fun PlaylistSection(
    playlists: List<Playlist>,
    songs: List<Song>,
    onCreate: (String) -> Unit,
    onOpen: (Playlist) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val songById = remember(songs) { songs.associateBy { it.id } }

    SectionTitle(
        title = tr(R.string.playlist_count, playlists.size),
        action = {
            Image(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = tr(R.string.new_playlist),
                colorFilter = themeIconTint(),
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable { showCreateDialog = true },
            )
        },
    )
    Column(Modifier.padding(horizontal = 20.dp)) {
        if (playlists.isEmpty()) {
            // 空态：Create Playlist 行（Figma 162:4964），点击新建
            CreatePlaylistRow(onClick = { showCreateDialog = true }, verticalPadding = 2.dp)
        } else {
            val shown = if (expanded) playlists else playlists.take(3)
            shown.forEachIndexed { index, playlist ->
                PlaylistRow(
                    playlist = playlist,
                    firstSong = playlist.songIds.firstOrNull()?.let { songById[it] },
                    onClick = { onOpen(playlist) },
                    verticalPadding = if (index == 0) 2.dp else 8.dp, // 首行靠近标题
                )
            }
            if (playlists.size > 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(top = 6.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr(if (expanded) R.string.collapse else R.string.view_all_playlists),
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                    )
                    Spacer(Modifier.width(2.dp))
                    Image(
                        painter = painterResource(R.drawable.ic_unfold),
                        contentDescription = null,
                        colorFilter = themeIconTint(BeezeColors.Muted),
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreate(name)
                showCreateDialog = false
            },
        )
    }
}

/** 空歌单态的 Create Playlist 行：primary-subtle 方块 + 黑胶盘 + 灰色文案 */
@Composable
internal fun CreatePlaylistRow(onClick: () -> Unit, verticalPadding: Dp = 8.dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp, 52.dp)) {
            Box(
                Modifier
                    .size(52.dp)
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .background(BeezeColors.Body)
            )
            Box(
                Modifier
                    .size(52.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BeezeColors.PrimarySubtle)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            tr(R.string.create_playlist),
            style = BeezeType.button,
            color = BeezeColors.Muted,
        )
    }
}

/** 歌单行：封面（右侧露黑胶盘）+ 名称 + 「N songs · 首曲名·艺术家」 */
@Composable
internal fun PlaylistRow(playlist: Playlist, firstSong: Song?, onClick: () -> Unit, verticalPadding: Dp = 8.dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp, 52.dp)) {
            // 黑胶盘：右侧露出一条边
            Box(
                Modifier
                    .size(52.dp)
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .background(BeezeColors.Body)
            )
            CoverImage(
                firstSong?.artwork,
                52.dp,
                RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                playlist.name,
                style = BeezeType.button,
                color = BeezeColors.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = songsCount(playlist.songIds.size) +
                (firstSong?.let { " · ${it.title}·${it.artist}" } ?: "")
            Text(
                subtitle,
                style = BeezeType.caption,
                color = BeezeColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 新建/重命名歌单弹窗，默认聚焦输入框并拉起键盘（Figma 173:3739） */
@Composable
internal fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = tr(R.string.create_playlist),
    initialName: String = "",
    confirmLabel: String = tr(R.string.create),
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BeezeColors.Surface)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                title,
                style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                color = BeezeColors.Body,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeezeColors.Canvas)
                    .padding(12.dp),
                singleLine = true,
                textStyle = BeezeType.bodyMd.copy(color = BeezeColors.Body),
                cursorBrush = SolidColor(BeezeColors.Primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) {
                            Text(
                                tr(R.string.playlist_name),
                                style = BeezeType.bodyMd,
                                color = BeezeColors.Muted,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.width(240.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    tr(R.string.cancel),
                    style = BeezeType.button.copy(fontWeight = FontWeight.Normal),
                    color = BeezeColors.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onDismiss),
                )
                Text(
                    confirmLabel,
                    style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                    color = BeezeColors.Primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onConfirm(name) },
                )
            }
        }
    }
}
