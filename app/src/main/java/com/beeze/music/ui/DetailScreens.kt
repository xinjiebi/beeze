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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.AlbumItem
import com.beeze.music.data.ArtistItem
import com.beeze.music.data.FolderItem
import com.beeze.music.data.Song
import com.beeze.music.data.SongArtwork
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

    Box(
        Modifier
            .fillMaxSize()
            .background(BeezeColors.SurfaceSoft)
    ) {
        // 顶部紫色渐变（与主页一致，约 360dp）
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .topPurpleGradient()
        )
        when (detail) {
            is DetailPage.Folder -> FolderDetail(viewModel, detail.folder, currentSong)
            is DetailPage.Album -> AlbumDetail(viewModel, detail.album, currentSong)
            is DetailPage.Artist -> ArtistDetail(viewModel, detail.artist, currentSong)
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
private fun FolderDetail(viewModel: PlayerViewModel, folder: FolderItem, currentSong: Song?) {
    DetailBody(
        viewModel = viewModel,
        title = folder.name,
        titleAlwaysVisible = true,
        songs = folder.songs,
        currentSong = currentSong,
    )
}

/** 专辑详情（Figma 82:4547 / 82:4923）：大封面头部随滚动收起，顶栏标题淡入，工具行置顶 */
@Composable
private fun AlbumDetail(viewModel: PlayerViewModel, album: AlbumItem, currentSong: Song?) {
    DetailBody(
        viewModel = viewModel,
        title = album.name,
        titleAlwaysVisible = false,
        songs = album.songs,
        currentSong = currentSong,
        // 头部高度：132 封面 + 8 + 28 名称 + 2 + 26 艺术家
        headerHeight = 196.dp,
        header = { AlbumHeader(album) },
    )
}

/** 艺术家详情（Figma 82:5103）：顶栏带艺术家名，头部为横向专辑卡片，可点进专辑详情 */
@Composable
private fun ArtistDetail(viewModel: PlayerViewModel, artist: ArtistItem, currentSong: Song?) {
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
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val headerHPx = with(density) { headerHeight.roundToPx() }
    // 头部收起进度：独立状态，由嵌套滚动驱动（不依赖列表 offset，避免与列表滚动互相钳制）
    var collapsePx by remember { mutableIntStateOf(0) }
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
        DetailTopBar(title = title, titleAlpha = titleAlpha, onBack = viewModel::closeDetail)
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
                DetailToolbar(songs.size)
                DetailActionButtons(
                    onShuffle = { viewModel.playShuffled(songs) },
                    onPlayAll = { viewModel.playSongs(songs, 0) },
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 100.dp), // 给 MiniPlayer 留位
                ) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            song = song,
                            isPlayingRow = song.id == currentSong?.id,
                            onClick = { viewModel.playSongs(songs, index) },
                            onMore = { showToast(context, "更多：${song.title}") },
                        )
                    }
                }
            }
        }
    }
}

/** 详情页顶栏：32 触控区返回箭头 + 标题（Figma Frame 43，高 56，px 20） */
@Composable
private fun DetailTopBar(title: String, titleAlpha: Float, onBack: () -> Unit) {
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
            modifier = Modifier.graphicsLayer { alpha = titleAlpha },
        )
    }
}

/** 「N songs」+ 多选/排序图标行（Figma Frame 44，高 48，px 20，图标间距 16） */
@Composable
private fun DetailToolbar(songCount: Int) {
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
                .iconClickable { showToast(context, "多选（占位）") },
        )
        Spacer(Modifier.width(16.dp))
        Image(
            painter = painterResource(R.drawable.ic_sort),
            contentDescription = "排序",
            modifier = Modifier
                .size(24.dp)
                .iconClickable { showToast(context, "排序（占位）") },
        )
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
private fun CoverWithDisc(artwork: SongArtwork?, coverSize: Dp) {
    val coverShape = RoundedCornerShape(8.dp)
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
