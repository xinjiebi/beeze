package com.beeze.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import com.beeze.music.DetailPage
import com.beeze.music.EntryKind
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.AlbumItem
import com.beeze.music.data.ArtistItem
import com.beeze.music.data.FolderItem
import com.beeze.music.data.Song
import com.beeze.music.data.SongArtwork
import com.beeze.music.data.SongSortOption
import com.beeze.music.data.SongSortScope
import com.beeze.music.data.sortedForDisplay
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/**
 * 二级详情页容器：顶部紫色渐变 + 顶栏 + 内容 + 底部悬浮 MiniPlayer。
 * 文件夹 / 专辑 / 艺术家三种详情共用这套骨架。
 */
@Composable
fun DetailScreen(viewModel: PlayerViewModel, detail: DetailPage) {
    BackHandler(onBack = viewModel::closeDetail)
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    var selectionSongs by remember(detail) { mutableStateOf<List<Song>?>(null) }

    val selectedList = selectionSongs
    if (selectedList != null) {
        BatchSelectionScreen(
            viewModel = viewModel,
            songs = selectedList,
            playlistId = (detail as? DetailPage.SongList)?.playlistId,
            onCancel = { selectionSongs = null },
            onDone = { ordered ->
                (detail as? DetailPage.SongList)?.playlistId?.let { playlistId ->
                    viewModel.reorderPlaylist(playlistId, ordered.map { it.id })
                }
                selectionSongs = null
            },
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BeezeColors.SurfaceSoft)
    ) {
        // 顶部紫色渐变（与主页一致，约 360dp）；For you 入口详情页用自己的主题色渐变
        if (detail !is DetailPage.EntryList) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .topPurpleGradient()
            )
        }
        when (detail) {
            DetailPage.About -> AboutScreen(viewModel)
            DetailPage.Privacy -> PrivacyScreen(viewModel)
            is DetailPage.Folder -> FolderDetail(viewModel, detail.folder, currentSong) { selectionSongs = it }
            is DetailPage.Album -> AlbumDetail(viewModel, detail.album, currentSong) { selectionSongs = it }
            is DetailPage.Artist -> ArtistDetail(viewModel, detail.artist, currentSong) { selectionSongs = it }
            is DetailPage.SongList -> SongListDetail(viewModel, detail, currentSong) { selectionSongs = it }
            is DetailPage.EntryList -> EntryDetail(viewModel, detail, currentSong) { selectionSongs = it }
        }
        currentSong?.let { song ->
            MiniPlayer(
                song = song,
                isPlaying = isPlaying,
                onTogglePlay = viewModel::togglePlayPause,
                onOpen = viewModel::openPlayer,
                onShowQueue = viewModel::openQueue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )
        }
    }
}

/** 文件夹详情（Figma 80:4110）：顶栏带文件夹名，无头部，工具行 + 按钮 + 歌曲列表 */
@Composable
private fun FolderDetail(viewModel: PlayerViewModel, folder: FolderItem, currentSong: Song?, onMultiSelect: (List<Song>) -> Unit) {
    DetailBody(
        viewModel = viewModel,
        title = folder.name,
        titleAlwaysVisible = true,
        songs = folder.songs,
        currentSong = currentSong,
        onMultiSelect = onMultiSelect,
        sortScope = SongSortScope.Folder,
    )
}

/** 动态歌曲列表详情（For you 各卡片 / 歌单）：按 id 列表从媒体库实时解析，数据变化随之刷新 */
@Composable
private fun SongListDetail(viewModel: PlayerViewModel, page: DetailPage.SongList, currentSong: Song?, onMultiSelect: (List<Song>) -> Unit) {
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val songIds = remember(playlists, page) {
        page.playlistId?.let { id -> playlists.firstOrNull { it.id == id }?.songIds } ?: page.songIds
    }
    val songs = remember(allSongs, songIds) {
        val byId = allSongs.associateBy { it.id }
        songIds.mapNotNull { byId[it] }
    }
    DetailBody(
        viewModel = viewModel,
        title = page.playlistId?.let { id -> playlists.firstOrNull { it.id == id }?.name } ?: page.title,
        titleAlwaysVisible = true,
        songs = songs,
        currentSong = currentSong,
        onMultiSelect = onMultiSelect,
        sortScope = if (page.playlistId == null && page.sortable) SongSortScope.Generic else null,
        playlistId = page.playlistId,
    )
}

/** For you 入口详情（Figma 167:6387 / 168:6795 / 168:7211）：
 *  主题色渐变背景 + 大封面头部 + 标题/副标题；上滑头部收起、渐变淡出，顶栏标题淡入 */
@Composable
private fun EntryDetail(viewModel: PlayerViewModel, page: DetailPage.EntryList, currentSong: Song?, onMultiSelect: (List<Song>) -> Unit) {
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()
    val covers by viewModel.cardCovers.collectAsStateWithLifecycle()
    val songs = remember(allSongs, page.songIds) {
        val byId = allSongs.associateBy { it.id }
        page.songIds.mapNotNull { byId[it] }
    }
    val cover = when (page.kind) {
        EntryKind.LyricSync -> covers.lyric
        EntryKind.Liked -> covers.liked
        EntryKind.MostPlayed -> covers.mostPlayed
    }
    var collapseFraction by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .graphicsLayer { alpha = 1f - collapseFraction }
                .entryGradient(page.kind)
        )
        DetailBody(
            viewModel = viewModel,
            title = page.kind.title,
            titleAlwaysVisible = false,
            songs = songs,
            currentSong = currentSong,
            onMultiSelect = onMultiSelect,
            sortScope = if (page.kind == EntryKind.MostPlayed) null else SongSortScope.Entry,
            // 头部高度：132 封面 + 8 + 28 标题 + 2 + 52 副标题（两行）
            headerHeight = 222.dp,
            header = { EntryHeader(page.kind, cover) },
            onCollapseFractionChanged = { collapseFraction = it },
        )
    }
}

/** 入口详情的主题色顶部渐变（170.5deg：主色 5.65% → 辅色 33.98% → 透明 72.9%） */
private fun Modifier.entryGradient(kind: EntryKind): Modifier = drawBehind {
    val stops: Array<Pair<Float, Color>> = when (kind) {
        EntryKind.LyricSync -> arrayOf(
            0.0565f to Color(0xFF07BA99).copy(alpha = 0.8f),
            0.3398f to Color(0xFF963EF7).copy(alpha = 0.5f),
            0.7293f to Color(0xFF963EF7).copy(alpha = 0f),
        )
        EntryKind.Liked -> arrayOf(
            0.0565f to Color(0xFFD325A8),
            0.3398f to Color(0xFFEFBF43).copy(alpha = 0.2f),
            0.7293f to Color(0xFFEFBF43).copy(alpha = 0f),
        )
        EntryKind.MostPlayed -> arrayOf(
            0.0565f to Color(0xFF18B022).copy(alpha = 0.8f),
            0.3398f to Color(0xFF1BA8EB).copy(alpha = 0.5f),
            0.7293f to Color(0xFF1BA8EB).copy(alpha = 0f),
        )
    }
    val brush = Brush.linearGradient(
        *stops,
        start = Offset(size.width * 0.45f, 0f),
        end = Offset(size.width * 0.6f, size.height),
    )
    drawRect(brush)
}

/** 入口详情头部：居中 132 封面（圆角 14 + 黑胶盘）+ 标题 + 两行副标题 */
@Composable
private fun EntryHeader(kind: EntryKind, cover: Song?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverWithDisc(cover?.artwork, 132.dp, cornerRadius = 14.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            kind.title,
            style = BeezeType.title,
            color = BeezeColors.Body,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            kind.subtitle,
            style = BeezeType.bodyMd,
            color = BeezeColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** 专辑详情（Figma 82:4547 / 82:4923）：大封面头部随滚动收起，顶栏标题淡入，工具行置顶 */
@Composable
private fun AlbumDetail(viewModel: PlayerViewModel, album: AlbumItem, currentSong: Song?, onMultiSelect: (List<Song>) -> Unit) {
    DetailBody(
        viewModel = viewModel,
        title = album.name,
        titleAlwaysVisible = false,
        songs = album.songs,
        currentSong = currentSong,
        onMultiSelect = onMultiSelect,
        sortScope = SongSortScope.Album,
        // 头部高度：132 封面 + 8 + 28 名称 + 2 + 26 艺术家
        headerHeight = 196.dp,
        header = { AlbumHeader(album) },
    )
}

/** 艺术家详情（Figma 82:5103）：顶栏带艺术家名，头部为横向专辑卡片，可点进专辑详情 */
@Composable
private fun ArtistDetail(viewModel: PlayerViewModel, artist: ArtistItem, currentSong: Song?, onMultiSelect: (List<Song>) -> Unit) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artistAlbums = remember(albums, artist.name) {
        albums.filter { it.artist == artist.name }
    }
    DetailBody(
        viewModel = viewModel,
        title = artist.name,
        titleAlwaysVisible = true,
        songs = artist.songs,
        currentSong = currentSong,
        onMultiSelect = onMultiSelect,
        sortScope = SongSortScope.Artist,
        // 头部高度：46 标签行 + 143 卡片（99 封面 + 4 + 22 名称 + 18 歌曲数）+ 12 间距
        headerHeight = 201.dp,
        header = {
            ArtistAlbumsHeader(
                albums = artistAlbums,
                onOpenAlbum = { viewModel.openDetail(DetailPage.Album(it)) },
            )
        },
    )
}

/**
 * 详情页骨架：顶栏 + 可收起头部 + 固定工具行 + 歌曲列表。
 * 头部随列表滚动 1:1 向上收起并裁剪，工具行（N songs + 按钮）固定不动且背景透明，
 * 顶部紫色渐变从头贯穿到工具行，不会被切断。
 * titleAlwaysVisible = false 时（专辑页），顶栏标题等头部收完后才淡入。
 */
@Composable
private fun DetailBody(
    viewModel: PlayerViewModel,
    title: String,
    titleAlwaysVisible: Boolean,
    songs: List<Song>,
    currentSong: Song?,
    headerHeight: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    onMultiSelect: (List<Song>) -> Unit = {},
    sortScope: SongSortScope? = null,
    playlistId: Long? = null,
    onCollapseFractionChanged: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val sortOptions by viewModel.sortOptions.collectAsStateWithLifecycle()
    val sortOption = sortScope?.let { sortOptions[it] } ?: SongSortOption.Default
    val displayedSongs = remember(songs, sortOption) { songs.sortedForDisplay(sortOption) }
    var showSortSheet by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistActions by remember { mutableStateOf(false) }
    var showRenamePlaylist by remember { mutableStateOf(false) }
    var showDeletePlaylist by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val headerHPx = with(density) { headerHeight.roundToPx() }
    // 头部收起进度：独立状态，由嵌套滚动驱动（不依赖列表 offset，避免与列表滚动互相钳制）
    var collapsePx by remember { mutableIntStateOf(0) }
    // 把收起比例同步给外层（入口详情的主题渐变随之淡出）
    LaunchedEffect(collapsePx, headerHPx) {
        onCollapseFractionChanged(if (headerHPx > 0) collapsePx / headerHPx.toFloat() else 0f)
    }
    // 向上滑先收头部，收完列表才滚动；列表到顶后继续下滑则展开头部。
    // 这样即使歌曲很少（内容不足一屏），也能收起头部、歌曲置顶
    val nestedScrollConnection = remember(headerHPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && collapsePx < headerHPx) {
                    val consume = minOf(-available.y, (headerHPx - collapsePx).toFloat())
                    collapsePx += consume.roundToInt()
                    return Offset(0f, -consume)
                }
                if (available.y > 0 && collapsePx > 0 &&
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                ) {
                    val consume = minOf(available.y, collapsePx.toFloat())
                    collapsePx -= consume.roundToInt()
                    return Offset(0f, consume)
                }
                return Offset.Zero
            }
        }
    }
    val headerCollapsed = headerHPx > 0 && collapsePx >= headerHPx
    val titleAlpha = animateFloatAsState(
        targetValue = if (titleAlwaysVisible || headerCollapsed) 1f else 0f,
        label = "detailTitle",
    ).value

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailTopBar(
            title = title,
            titleAlpha = titleAlpha,
            onBack = viewModel::closeDetail,
            onMore = if (playlistId != null) ({ showPlaylistActions = true }) else null,
        )
        // 内容区橡皮筋拉伸：歌曲不足一屏无法滚动时，拖拽仍可拉伸回弹
        Box(Modifier.fillMaxWidth().weight(1f).pageStretch()) {
            Column(Modifier.fillMaxSize()) {
                if (header != null) {
                    // 可收起头部：容器高度随收起进度缩小并裁剪，内容以固有高度绘制并上移；
                    // 背景透明，顶部紫色渐变从头贯穿到工具行，不会被切断
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(headerHeight - with(density) { collapsePx.toDp() })
                            .clipToBounds()
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                // unbounded：内容始终按固有高度布局，不随容器收窄被压扁
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .offset { IntOffset(0, -collapsePx) }
                        ) {
                            header()
                        }
                    }
                }
                DetailToolbar(
                    songCount = displayedSongs.size,
                    onMultiSelect = { onMultiSelect(displayedSongs) },
                    onSort = sortScope?.let { { showSortSheet = true } },
                )
                DetailActionButtons(
                    onShuffle = { viewModel.playShuffled(displayedSongs) },
                    onPlayAll = { viewModel.playSongs(displayedSongs, 0) },
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 100.dp), // 给 MiniPlayer 留位
                ) {
                    itemsIndexed(displayedSongs, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            song = song,
                            isPlayingRow = song.id == currentSong?.id,
                            onClick = { viewModel.playSongs(displayedSongs, index, openPlayer = true) },
                            onMore = { actionSong = song },
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet && sortScope != null) {
        SortSheet(viewModel, sortScope, onDismiss = { showSortSheet = false })
    }
    actionSong?.let { song ->
        SongActionsSheet(viewModel, song, playlistId = playlistId, onDismiss = { actionSong = null })
    }
    if (showPlaylistActions && playlistId != null) {
        PlaylistActionsSheet(
            name = title,
            onRename = {
                showPlaylistActions = false
                showRenamePlaylist = true
            },
            onDelete = {
                showPlaylistActions = false
                showDeletePlaylist = true
            },
            onDismiss = { showPlaylistActions = false },
        )
    }
    if (showRenamePlaylist && playlistId != null) {
        CreatePlaylistDialog(
            title = "Rename playlist",
            initialName = title,
            confirmLabel = "Save",
            onDismiss = { showRenamePlaylist = false },
            onConfirm = { name ->
                if (viewModel.renamePlaylist(playlistId, name)) {
                    showToast(context, "Playlist renamed")
                    showRenamePlaylist = false
                } else {
                    showToast(context, "Name cannot be empty")
                }
            },
        )
    }
    if (showDeletePlaylist && playlistId != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeletePlaylist = false },
            containerColor = BeezeColors.Surface,
            title = { Text("Delete playlist?", style = BeezeType.title, color = BeezeColors.Body) },
            text = {
                Text(
                    "Only this playlist will be deleted. Songs stay in your library.",
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
                        viewModel.deletePlaylist(playlistId)
                        showToast(context, "Playlist deleted")
                        showDeletePlaylist = false
                    },
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    style = BeezeType.button,
                    color = BeezeColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable {
                        showDeletePlaylist = false
                    },
                )
            },
        )
    }
}

/** 详情页顶栏：32 触控区返回箭头 + 标题，自建歌单右侧提供管理入口 */
@Composable
private fun DetailTopBar(title: String, titleAlpha: Float, onBack: () -> Unit, onMore: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = "返回",
            modifier = Modifier
                .size(32.dp)
                .iconClickable(onClick = onBack)
                .padding(4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = BeezeType.button,
            color = BeezeColors.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = titleAlpha },
        )
        if (onMore != null) {
            Spacer(Modifier.width(8.dp))
            Image(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = "歌单管理",
                modifier = Modifier
                    .size(32.dp)
                    .iconClickable(onClick = onMore)
                    .padding(4.dp),
            )
        }
    }
}

/** 「N songs」+ 多选/排序图标行（Figma Frame 44，高 48，px 20，图标间距 16） */
@Composable
private fun DetailToolbar(songCount: Int, onMultiSelect: () -> Unit, onSort: (() -> Unit)?) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$songCount songs",
            style = BeezeType.bodySm,
            color = BeezeColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_multiple),
            contentDescription = "多选",
            modifier = Modifier
                .size(24.dp)
                .iconClickable(onClick = onMultiSelect),
        )
        if (onSort != null) {
            Spacer(Modifier.width(16.dp))
            Image(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = "排序",
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onSort),
            )
        }
    }
}

/** Shuffle / Play all 双按钮行（Figma Frame 48：px 16 py 8，间距 12，胶囊等分） */
@Composable
private fun DetailActionButtons(onShuffle: () -> Unit, onPlayAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailActionButton(
            iconRes = R.drawable.ic_shuffle,
            iconTint = BeezeColors.Primary,
            label = "Shuffle",
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
        DetailActionButton(
            iconRes = R.drawable.ic_play_all,
            iconTint = null, // 图标自带紫色
            label = "Play all",
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 胶囊按钮：primary-subtle 底 + 图标 + 文字（p12，圆角 full） */
@Composable
private fun DetailActionButton(
    iconRes: Int,
    iconTint: androidx.compose.ui.graphics.Color?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(BeezeColors.PrimarySubtle)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = iconTint?.let { ColorFilter.tint(it) },
            modifier = Modifier.size(24.dp),
        )
        Text(label, style = BeezeType.button, color = BeezeColors.Body)
    }
}

/**
 * 带黑胶盘露出的封面（专辑详情头部 / 艺术家页专辑卡片共用）。
 * 比例取自 Figma Frame 20：盘直径 = 0.864 × 封面边长，整体宽 = 1.091 × 封面边长。
 */
@Composable
private fun CoverWithDisc(artwork: SongArtwork?, coverSize: Dp, cornerRadius: Dp = 8.dp) {
    val coverShape = RoundedCornerShape(cornerRadius)
    Box(Modifier.size(width = coverSize * 1.091f, height = coverSize)) {
        // 黑胶盘：右侧露出
        Box(
            Modifier
                .size(coverSize * 0.864f)
                .align(Alignment.CenterEnd)
                .clip(CircleShape)
                .background(BeezeColors.Body)
        )
        // 封面；无封面时用浅紫 + 弱版蜜蜂占位
        CoverImage(
            artwork,
            coverSize,
            coverShape,
            placeholder = {
                Box(
                    Modifier
                        .size(coverSize)
                        .clip(coverShape)
                        .background(BeezeColors.PrimarySubtle),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_bee_soft),
                        contentDescription = null,
                        modifier = Modifier.size(coverSize * 0.75f),
                    )
                }
            },
        )
    }
}

/** 专辑详情头部（Figma Frame 51：封面块 144x132 居中，下 8 接名称块） */
@Composable
private fun AlbumHeader(album: AlbumItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverWithDisc(album.artwork, 132.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            style = BeezeType.title,
            color = BeezeColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            album.artist,
            style = BeezeType.bodyMd,
            color = BeezeColors.Muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** 艺术家详情头部（Figma Frame 57：「N albums」标签 + 横向专辑卡片行） */
@Composable
private fun ArtistAlbumsHeader(albums: List<AlbumItem>, onOpenAlbum: (AlbumItem) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        // 「N albums」标签行（高 46，px 20）
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "${albums.size} album${if (albums.size > 1) "s" else ""}",
                style = BeezeType.bodySm,
                color = BeezeColors.Muted,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(albums, key = { it.key }) { album ->
                Column(
                    modifier = Modifier
                        .width(109.dp)
                        .clickable { onOpenAlbum(album) },
                ) {
                    CoverWithDisc(album.artwork, 99.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        album.name,
                        style = BeezeType.bodySm.copy(fontWeight = FontWeight.Medium),
                        color = BeezeColors.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${album.songs.size} song${if (album.songs.size > 1) "s" else ""}",
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                    )
                }
            }
        }
        // 专辑区与歌曲区间距 12（Figma Frame 52 位于 Frame 57 下方 12）
        Spacer(Modifier.height(12.dp))
    }
}
