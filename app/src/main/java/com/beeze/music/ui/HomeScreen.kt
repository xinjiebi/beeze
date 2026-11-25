package com.beeze.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.delay

/** 主页：Navbar + 标签行 + 列表内容 + 底部悬浮 MiniPlayer */
@Composable
fun HomeScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val adsManager = (context.applicationContext as com.beeze.music.BeezeApplication).adsManager
    val adsReady by adsManager.adsReady.collectAsStateWithLifecycle()
    LaunchedEffect(adsReady) {
        if (adsReady) {
            adsManager.preloadHomeAds((context.resources.configuration.screenWidthDp - 40).coerceAtLeast(1))
        }
    }
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val libraryLoaded by viewModel.libraryLoaded.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val initialHomeScan by viewModel.initialHomeScan.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val absorbProgress = remember(initialHomeScan) { Animatable(0f) }
    val scanButtonPulse = remember { Animatable(1f) }
    var scanButtonCenter by remember { mutableStateOf<Offset?>(null) }
    var scanOriginCenter by remember { mutableStateOf<Offset?>(null) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (initialHomeScan && scanning) 0f else 1f,
        animationSpec = tween(500),
        label = "initialHomeScanContent",
    )
    LaunchedEffect(initialHomeScan, scanning, libraryLoaded, scanError) {
        if (initialHomeScan && !scanning && libraryLoaded && !scanError) {
            delay(250)
            absorbProgress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            scanButtonPulse.animateTo(1.3f, tween(100))
            scanButtonPulse.animateTo(1f, tween(160))
            viewModel.completeInitialHomeScan()
        }
    }
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
                onScan = viewModel::openScan,
                onAbout = { viewModel.openDetail(DetailPage.About) },
                scanning = scanning,
                scanScale = scanButtonPulse.value * (1f + absorbProgress.value * 0.12f),
                scanHaloAlpha = absorbProgress.value * 0.16f,
                onScanPositioned = { scanButtonCenter = it },
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = contentAlpha },
                userScrollEnabled = !initialHomeScan,
            ) { page ->
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

        if (initialHomeScan) {
            Column(Modifier.fillMaxSize().statusBarsPadding().zIndex(2f)) {
                Spacer(Modifier.height(108.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(300.dp)
                            .onGloballyPositioned {
                                scanOriginCenter = it.positionInRoot() +
                                    Offset(it.size.width / 2f, it.size.height / 2f)
                            },
                    ) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val origin = scanOriginCenter
                                val target = scanButtonCenter
                                if (origin != null && target != null) {
                                    translationX = (target.x - origin.x) * absorbProgress.value
                                    translationY = (target.y - origin.y) * absorbProgress.value
                                }
                                val scale = 1f - absorbProgress.value * 0.92f
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - absorbProgress.value * 0.85f
                            },
                        ) {
                            ScanVisualization(scanning)
                        }
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 56.dp)
                            .graphicsLayer { alpha = 1f - absorbProgress.value },
                    ) {
                        if (scanError) ScanError(viewModel::loadSongs)
                        else ScanProgress(scanProgress.scanned, scanProgress.total)
                    }
                }
            }
        }
    }
}

/** Navbar：左侧 logo + Beeze 字标，右侧搜索、扫描与关于入口 */
@Composable
private fun HomeNavbar(
    onSearch: () -> Unit,
    onScan: () -> Unit,
    onAbout: () -> Unit,
    scanning: Boolean,
    scanScale: Float,
    scanHaloAlpha: Float,
    onScanPositioned: (Offset) -> Unit,
) {
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
            contentDescription = tr(R.string.search),
            colorFilter = themeIconTint(),
            modifier = Modifier
                .padding(4.dp)
                .size(24.dp)
                .iconClickable(onClick = onSearch),
        )
        Spacer(Modifier.width(8.dp))
        Image(
            painter = painterResource(R.drawable.ic_scan),
            contentDescription = tr(if (scanning) R.string.scanning_local_files else R.string.scan_local_music),
            colorFilter = themeIconTint(),
            modifier = Modifier
                .onGloballyPositioned {
                    onScanPositioned(
                        it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                    )
                }
                .graphicsLayer {
                    scaleX = scanScale
                    scaleY = scanScale
                }
                .clip(CircleShape)
                .background(BeezeColors.Primary.copy(alpha = scanHaloAlpha))
                .padding(4.dp)
                .size(24.dp)
                .iconClickable(onClick = onScan),
        )
        Spacer(Modifier.width(8.dp))
        Image(
            painter = painterResource(R.drawable.ic_setting),
            contentDescription = tr(R.string.about),
            colorFilter = themeIconTint(),
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
                    text = tr(tab.labelRes),
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
    val adsManager = (context.applicationContext as com.beeze.music.BeezeApplication).adsManager
    val adsReady by adsManager.adsReady.collectAsStateWithLifecycle()
    val sortOptions by viewModel.sortOptions.collectAsStateWithLifecycle()
    val sortOption = sortOptions[SongSortScope.Songs] ?: SongSortOption.Default
    val sortedSongs = remember(songs, sortOption) { songs.sortedForDisplay(sortOption) }
    val adIndices = remember(sortedSongs) { adInsertionIndices(sortedSongs.size, 7, sortedSongs.hashCode()) }
    LaunchedEffect(adsReady, adIndices) {
        if (adsReady) adsManager.preloadNativeAds("home_songs", adIndices)
    }
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
                    contentDescription = tr(R.string.shuffle),
                    colorFilter = themeIconTint(),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    tr(R.string.shuffle_songs_count, sortedSongs.size),
                    style = BeezeType.bodyMd,
                    color = BeezeColors.Ink,
                )
            }
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_multiple),
                contentDescription = tr(R.string.multi_select),
                colorFilter = themeIconTint(),
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onMultiSelect),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = tr(R.string.sort),
                colorFilter = themeIconTint(),
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
                    Text(tr(R.string.no_audio_files), style = BeezeType.bodyMd, color = BeezeColors.Muted)
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
                    if (index in adIndices) {
                        ListNativeAd("home_songs_$index", Modifier.padding(vertical = 8.dp))
                    }
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
            contentDescription = tr(R.string.more),
            colorFilter = themeIconTint(),
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
                Text(tr(R.string.no_audio_files), style = BeezeType.bodyMd, color = BeezeColors.Muted)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        itemsIndexed(folders, key = { _, folder -> folder.path }) { index, folder ->
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
                    colorFilter = themeIconTint(BeezeColors.PrimarySubtle),
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
                        "${songsCount(folder.songs.size)} · ${folder.path}",
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = tr(R.string.more),
                    colorFilter = themeIconTint(),
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable { actionFolder = folder },
                )
            }
        }
        item(key = "folder_ad_bottom") {
            ListBannerAd("home_folders", Modifier.padding(vertical = 8.dp))
        }
    }
    actionFolder?.let { folder ->
        CollectionActionsSheet(
            viewModel = viewModel,
            title = folder.name,
            songs = folder.songs,
            onDismiss = { actionFolder = null },
            onDelete = { viewModel.deleteFolderFromApp(folder) },
        )
    }
}

/** Albums 标签 */
@Composable
private fun AlbumsContent(viewModel: PlayerViewModel, albums: List<AlbumItem>, libraryLoaded: Boolean) {
    val adsManager = (LocalContext.current.applicationContext as com.beeze.music.BeezeApplication).adsManager
    val adsReady by adsManager.adsReady.collectAsStateWithLifecycle()
    val adIndices = remember(albums) { adInsertionIndices(albums.size, 8, albums.hashCode()) }
    LaunchedEffect(adsReady, adIndices) {
        if (adsReady) adsManager.preloadNativeAds("home_albums", adIndices)
    }
    var actionAlbum by remember { mutableStateOf<AlbumItem?>(null) }
    if (albums.isEmpty()) {
        if (libraryLoaded) {
            StaticPage {
                Text(tr(R.string.no_audio_files), style = BeezeType.bodyMd, color = BeezeColors.Muted)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        itemsIndexed(albums, key = { _, album -> album.key }) { index, album ->
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
                        "${album.artist} · ${songsCount(album.songs.size)}",
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = tr(R.string.more),
                    colorFilter = themeIconTint(),
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable { actionAlbum = album },
                )
            }
            if (index in adIndices) {
                ListNativeAd("home_albums_$index", Modifier.padding(vertical = 8.dp))
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
    val adsManager = (LocalContext.current.applicationContext as com.beeze.music.BeezeApplication).adsManager
    val adsReady by adsManager.adsReady.collectAsStateWithLifecycle()
    val adIndices = remember(artists) { adInsertionIndices(artists.size, 11, artists.hashCode()) }
    LaunchedEffect(adsReady, adIndices) {
        if (adsReady) adsManager.preloadNativeAds("home_artists", adIndices)
    }
    var actionArtist by remember { mutableStateOf<ArtistItem?>(null) }
    if (artists.isEmpty()) {
        if (libraryLoaded) {
            StaticPage {
                Text(tr(R.string.no_audio_files), style = BeezeType.bodyMd, color = BeezeColors.Muted)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        itemsIndexed(artists, key = { _, artist -> artist.name }) { index, artist ->
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
                        songsCount(artist.songs.size),
                        style = BeezeType.caption,
                        color = BeezeColors.Muted,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = tr(R.string.more),
                    colorFilter = themeIconTint(),
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable { actionArtist = artist },
                )
            }
            if (index in adIndices) {
                ListNativeAd("home_artists_$index", Modifier.padding(vertical = 8.dp))
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
                    color = BeezeColors.MiniPlayerTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = BeezeType.caption,
                    color = BeezeColors.MiniPlayerArtist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Image(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause_mini else R.drawable.ic_play_mini),
                contentDescription = tr(if (isPlaying) R.string.pause else R.string.play),
                colorFilter = themeIconTint(),
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onTogglePlay),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.ic_playlist_mini),
                contentDescription = tr(R.string.queue),
                colorFilter = themeIconTint(BeezeColors.MiniPlayerArtist),
                modifier = Modifier
                    .size(24.dp)
                    .iconClickable(onClick = onShowQueue),
            )
        }
    }
}
