package com.beeze.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.beeze.music.DetailPage
import com.beeze.music.HomeTab
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.AlbumItem
import com.beeze.music.data.ArtistItem
import com.beeze.music.data.FolderItem
import com.beeze.music.data.Song
import com.beeze.music.data.SongSortOption
import com.beeze.music.data.SongSortScope
import com.beeze.music.data.sortedForDisplay
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 主页：Navbar + 标签行 + 列表内容 + 底部悬浮 MiniPlayer */
@Composable
fun HomeScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val libraryLoaded by viewModel.libraryLoaded.collectAsStateWithLifecycle()
    var selectingSongs by remember { mutableStateOf(false) }

    if (selectingSongs) {
        BatchSelectionScreen(
            viewModel = viewModel,
            songs = songs,
            playlistId = null,
            onCancel = { selectingSongs = false },
            onDone = { selectingSongs = false },
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BeezeColors.SurfaceSoft)
    ) {
        // 顶部紫色渐变（覆盖约 360dp）
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .topPurpleGradient()
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeNavbar(
                onSearch = viewModel::openSearch,
                onAbout = { viewModel.openDetail(DetailPage.About) },
            )
            HomeTabBar(selectedTab, onSelect = viewModel::selectTab)

            // 内容区支持左右滑动切换标签；与 selectedTab 双向同步
            val pagerState = rememberPagerState(
                initialPage = HomeTab.entries.indexOf(selectedTab).coerceAtLeast(0),
                pageCount = { HomeTab.entries.size },
            )
            // 滑动停稳后同步到 ViewModel（进而驱动标签行）
            LaunchedEffect(pagerState.settledPage) {
                val tab = HomeTab.entries[pagerState.settledPage]
                if (tab != selectedTab) viewModel.selectTab(tab)
            }
            // 点击标签时滚动到对应页
            LaunchedEffect(selectedTab) {
                val page = HomeTab.entries.indexOf(selectedTab)
                if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                // 每页套橡皮筋拉伸：列表不足一屏无法滚动时，拖拽仍有拉伸回弹
                Box(Modifier.fillMaxSize().pageStretch()) {
                    when (HomeTab.entries[page]) {
                        HomeTab.ForYou -> ForYouContent(
                            viewModel = viewModel,
                            isPageSettled = pagerState.settledPage == page,
                        )
                        HomeTab.Songs -> SongsContent(viewModel, songs, currentSong, libraryLoaded) {
                            selectingSongs = true
                        }
                        HomeTab.Folders -> FoldersContent(viewModel, folders, libraryLoaded)
                        HomeTab.Albums -> AlbumsContent(viewModel, albums, libraryLoaded)
                        HomeTab.Artists -> ArtistsContent(viewModel, artists, libraryLoaded)
                    }
                }
            }
        }

        // 底部悬浮 MiniPlayer：无播放任务时隐藏
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

/** Navbar：左侧 logo + Beeze 字标，右侧搜索与关于入口 */
@Composable
private fun HomeNavbar(onSearch: () -> Unit, onAbout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "Beeze",
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text("Beeze", style = BeezeType.brand, color = BeezeColors.Ink)
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = "搜索",
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .iconClickable(onClick = onSearch),
        )
        Spacer(Modifier.width(8.dp))
        Image(
            painter = painterResource(R.drawable.ic_setting),
            contentDescription = "关于",
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .iconClickable(onClick = onAbout),
        )
    }
}

/** 标签行：选中标签加粗变大并带左右装饰小标记；选中变化时自动滚动，保证选中项完整可见且露出后一项 */
@Composable
private fun HomeTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // 各标签在内容坐标系中的横向范围（含 20dp 水平内边距的偏移）
    val tabBounds = remember { mutableStateMapOf<HomeTab, IntRange>() }

    // 选中变化（点击或页面滑动）时滚动标签行：
    // 右方向保证「选中项 + 后一项的露出量」可见，左方向保证选中项完整可见。
    // 注意 effect 的 key 要包含位置值本身：选中后标签会变大（粗体+装饰标记），
    // 位置会二次更新，只监听 size 会用旧位置算出偏差
    val nextTab = HomeTab.entries.getOrNull(selected.ordinal + 1)
    val selectedBounds = tabBounds[selected]
    val nextBounds = nextTab?.let { tabBounds[it] }
    LaunchedEffect(selected, selectedBounds, nextBounds) {
        val bounds = selectedBounds ?: return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport == 0) return@LaunchedEffect
        val revealPx = with(density) { 40.dp.roundToPx() }      // 期望露出后一项的量
        val edgePadPx = with(density) { 20.dp.roundToPx() }     // 行首内边距
        var target = scrollState.value
        if (nextBounds == null) {
            // 最后一项：直接滚到底，行尾 24dp 内边距即右侧留白
            target = scrollState.maxValue
        } else {
            val rightNeed = minOf(nextBounds.last, nextBounds.first + revealPx)
            if (rightNeed > target + viewport) target = rightNeed - viewport
        }
        if (bounds.first - edgePadPx < target) target = bounds.first - edgePadPx
        target = target.coerceIn(0, scrollState.maxValue)
        if (target != scrollState.value) scrollState.animateScrollTo(target)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .horizontalScroll(scrollState)
            .padding(start = 20.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        HomeTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        val left = coordinates.positionInParent().x.toInt()
                        tabBounds[tab] = left..(left + coordinates.size.width)
                    }
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab) },
            ) {
                if (isSelected) {
                    Image(
                        painter = painterResource(R.drawable.ic_tabmark_left),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp, 8.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = tab.label,
                    style = if (isSelected) BeezeType.title else BeezeType.bodyMd,
                    color = if (isSelected) BeezeColors.Primary else BeezeColors.Muted,
                )
                if (isSelected) {
                    Spacer(Modifier.width(4.dp))
                    Image(
                        painter = painterResource(R.drawable.ic_tabmark_right),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp, 8.dp),
                    )
                }
            }
        }
    }
}

/** For you 页：实现见 ForYouScreen.kt（快捷卡片 + 最近播放 + 歌单 + 每周统计） */

/** Songs 标签：工具行 + 歌曲列表 */
@Composable
private fun SongsContent(
    viewModel: PlayerViewModel,
    songs: List<Song>,
    currentSong: Song?,
    libraryLoaded: Boolean,
    onMultiSelect: () -> Unit,
) {
    val context = LocalContext.current
    val sortOptions by viewModel.sortOptions.collectAsStateWithLifecycle()
    val sortOption = sortOptions[SongSortScope.Songs] ?: SongSortOption.Default
    val sortedSongs = remember(songs, sortOption) { songs.sortedForDisplay(sortOption) }
    var showSortSheet by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 工具行（高 48，px 20）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.iconClickable { viewModel.playShuffled(sortedSongs) },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = "随机播放",
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Shuffle (${sortedSongs.size} songs)",
                    style = BeezeType.bodyMd,
                    color = BeezeColors.Ink,
                )
            }
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_multiple),
                contentDescription = "多选",
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onMultiSelect),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = "排序",
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable { showSortSheet = true },
            )
        }

        if (sortedSongs.isEmpty()) {
            if (libraryLoaded) {
                // 空态也可被 pageStretch 拉伸（weight 占满剩余空间，内容垂直居中）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("本机暂无音频文件", style = BeezeType.bodyMd, color = BeezeColors.Muted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 100.dp), // 给 MiniPlayer 留位
            ) {
                itemsIndexed(sortedSongs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        isPlayingRow = song.id == currentSong?.id,
                        onClick = { viewModel.playSongs(sortedSongs, index, openPlayer = true) },
                        onMore = { actionSong = song },
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        SortSheet(viewModel, SongSortScope.Songs, onDismiss = { showSortSheet = false })
    }
    actionSong?.let { song ->
        SongActionsSheet(viewModel, song, onDismiss = { actionSong = null })
    }
}

/** 歌曲行：封面 + 标题 + 艺术家 + more；播放行高亮并叠均衡器标识（主页与详情页共用） */
@Composable
internal fun SongRow(song: Song, isPlayingRow: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    val coverShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp)) {
            CoverImage(song.artwork, 44.dp, coverShape)
            if (isPlayingRow) {
                // 50% 透明 ink 遮罩 + 三根紫色竖条（仿均衡器）
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(coverShape)
                        .background(BeezeColors.Ink.copy(alpha = 0.5f))
                )
                EqualizerBars()
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                song.title,
                style = BeezeType.button,
                color = if (isPlayingRow) BeezeColors.Primary else BeezeColors.Body,
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
        Image(
            painter = painterResource(R.drawable.ic_more),
            contentDescription = "更多",
            modifier = Modifier
                .size(24.dp)
                .iconClickable(onClick = onMore),
        )
    }
}

/** 播放行封面上的三根紫色竖条：左 3x8@(13,18)，中 3x16@(20,14)，右 3x8@(27,18)，圆角 1 */
@Composable
private fun EqualizerBars() {
    val barShape = RoundedCornerShape(1.dp)
    Box(Modifier.size(44.dp)) {
        listOf(Triple(13, 18, 8), Triple(20, 14, 16), Triple(27, 18, 8)).forEach { (x, y, h) ->
            Box(
                Modifier
                    .offset(x = x.dp, y = y.dp)
                    .size(3.dp, h.dp)
                    .clip(barShape)
                    .background(BeezeColors.Primary)
            )
        }
    }
}

/** Folders 标签 */
@Composable
private fun FoldersContent(viewModel: PlayerViewModel, folders: List<FolderItem>, libraryLoaded: Boolean) {
    var actionFolder by remember { mutableStateOf<FolderItem?>(null) }
    if (folders.isEmpty()) {
        // 加载完成前（缓存/扫描未返回）不显示空态文案，避免闪烁
        if (libraryLoaded) {
            StaticPage {
                Text("本机暂无音频文件", style = BeezeType.bodyMd, color = BeezeColors.Muted)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        items(folders, key = { it.path }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { viewModel.openDetail(DetailPage.Folder(folder)) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        folder.name,
                        style = BeezeType.button,
                        color = BeezeColors.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${folder.songs.size} songs · ${folder.path}",
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = "更多",
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable { actionFolder = folder },
                )
            }
        }
    }
    actionFolder?.let { folder ->
        CollectionActionsSheet(
            viewModel = viewModel,
            title = folder.name,
            songs = folder.songs,
            onDismiss = { actionFolder = null },
        )
    }
}

/** Albums 标签 */
@Composable
private fun AlbumsContent(viewModel: PlayerViewModel, albums: List<AlbumItem>, libraryLoaded: Boolean) {
    var actionAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    if (albums.isEmpty()) {
        if (libraryLoaded) {
            StaticPage {
                Text("本机暂无音频文件", style = BeezeType.bodyMd, color = BeezeColors.Muted)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        items(albums, key = { it.key }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { viewModel.openDetail(DetailPage.Album(album)) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumCover(album)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        album.name,
                        style = BeezeType.button,
                        color = BeezeColors.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${album.artist} · ${album.songs.size} song${if (album.songs.size > 1) "s" else ""}",
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = "更多",
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable { actionAlbum = album },
                )
            }
        }
    }
    actionAlbum?.let { album ->
        CollectionActionsSheet(
            viewModel = viewModel,
            title = album.name,
            songs = album.songs,
            onDismiss = { actionAlbum = null },
        )
    }
}

/** 专辑封面 48x44：黑胶盘恒在右侧露出；有封面时封面作封套盖在上面，无封面为封套占位（浅紫方盒 + 弱版蜜蜂） */
@Composable
private fun AlbumCover(album: AlbumItem) {
    var failed by remember(album.albumId) { mutableStateOf(false) }
    val artwork = album.artwork
    Box(Modifier.size(48.dp, 44.dp)) {
        // 黑胶盘：直径 38，圆心 (29,22)，右侧从封套后露出
        Box(
            Modifier
                .padding(start = 10.dp)
                .size(38.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(BeezeColors.Body)
        )
        // 封套占位：加载中或无封面时可见；封面加载成功后盖在上面
        Box(
            Modifier
                .size(44.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(8.dp))
                .background(BeezeColors.PrimarySubtle),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_bee_soft),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
        if (artwork != null && !failed) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

/** Artists 标签 */
@Composable
private fun ArtistsContent(viewModel: PlayerViewModel, artists: List<ArtistItem>, libraryLoaded: Boolean) {
    var actionArtist by remember { mutableStateOf<ArtistItem?>(null) }
    if (artists.isEmpty()) {
        if (libraryLoaded) {
            StaticPage {
                Text("本机暂无音频文件", style = BeezeType.bodyMd, color = BeezeColors.Muted)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        items(artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { viewModel.openDetail(DetailPage.Artist(artist)) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtistAvatarPlaceholder(44.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = BeezeType.button,
                        color = BeezeColors.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${artist.songs.size} songs",
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = "更多",
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable { actionArtist = artist },
                )
            }
        }
    }
    actionArtist?.let { artist ->
        CollectionActionsSheet(
            viewModel = viewModel,
            title = artist.name,
            songs = artist.songs,
            onDismiss = { actionArtist = null },
        )
    }
}

/** 底部悬浮 MiniPlayer：宽 = 屏宽 - 40dp（设计比例 320/360），高 52，圆角 26，canvas 色 + 投影（主页与详情页共用） */
@Composable
internal fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
    onShowQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rotation by rememberCoverRotation(isPlaying)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(52.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(26.dp),
        color = BeezeColors.Canvas,
        shadowElevation = 6.dp, // drop-shadow(0 0 6px rgba(0,0,0,0.1))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.rotate(rotation)) {
                CoverImage(song.artwork, 44.dp, CircleShape)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = BeezeType.bodySm.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF333333),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = BeezeType.caption,
                    color = Color(0xFF666666),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Image(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause_mini else R.drawable.ic_play_mini),
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onTogglePlay),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.ic_playlist_mini),
                contentDescription = "播放列表",
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onShowQueue),
            )
        }
    }
}
