package com.beeze.music

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.beeze.music.data.ALBUM_ART_BASE_URI
import com.beeze.music.data.AlbumItem
import com.beeze.music.data.ArtistItem
import com.beeze.music.data.FolderItem
import com.beeze.music.data.ForYouCovers
import com.beeze.music.data.ListeningStore
import com.beeze.music.data.LyricsRepository
import com.beeze.music.data.MediaStoreRepository
import com.beeze.music.data.Playlist
import com.beeze.music.data.PlaylistStore
import com.beeze.music.data.Song
import com.beeze.music.data.SongArtworkFetcher
import com.beeze.music.data.SongSearchResult
import com.beeze.music.data.SongSortOption
import com.beeze.music.data.SongSortScope
import com.beeze.music.data.SongSortStore
import com.beeze.music.data.SongTitleStore
import com.beeze.music.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 主页标签 */
enum class HomeTab(val label: String) {
    ForYou("For you"), Songs("Songs"), Folders("Folders"), Albums("Albums"), Artists("Artists")
}

/** 界面切换：主页 / 播放器页 */
enum class AppScreen { Home, Player }

/** 播放模式：列表循环 → 单曲循环 → 随机，循环切换（Figma 6:195 / 6:199 / 6:202）。
 *  每次启动默认列表循环，不持久化；只有从「Shuffle」入口播放才是随机 */
enum class PlayMode {
    RepeatAll, RepeatOne, Shuffle;

    fun next(): PlayMode = when (this) {
        RepeatAll -> RepeatOne
        RepeatOne -> Shuffle
        Shuffle -> RepeatAll
    }
}

/** For you 三个固定入口（各自有主题色与副标题的详情页） */
enum class EntryKind(val title: String, val subtitle: String) {
    LyricSync("Lyric Sync", "Watch lyrics animate in sync as your local music plays"),
    Liked("Liked", "Collect tunes you love, keep all your liked and saved local music here"),
    MostPlayed("Most Played", "Track your listening habits and show the local songs you play the most"),
}

/** 二级详情页：文件夹 / 专辑 / 艺术家 / 动态歌曲列表（For you 各卡片与歌单共用） */
sealed interface DetailPage {
    data class Folder(val folder: FolderItem) : DetailPage
    data class Album(val album: AlbumItem) : DetailPage
    data class Artist(val artist: ArtistItem) : DetailPage

    /** 以歌曲 id 列表承载的列表页，渲染时从媒体库实时解析（数据变化能随之刷新） */
    data class SongList(
        val title: String,
        val songIds: List<Long>,
        val playlistId: Long? = null,
        val sortable: Boolean = true,
    ) : DetailPage

    /** For you 入口详情：大封面头部 + 主题色渐变 + 滚动收起（Figma 167:6387 等） */
    data class EntryList(val kind: EntryKind, val songIds: List<Long>) : DetailPage
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application)
    private val listeningStore = ListeningStore(application)
    private val playlistStore = PlaylistStore(application)
    private val songSortStore = SongSortStore(application)
    private val songTitleStore = SongTitleStore(application)

    // ---------- 权限与媒体库 ----------
    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    /** 媒体库是否已完成首次加载（缓存或扫描）。空态文案只在加载完成后才显示，避免闪烁 */
    private val _libraryLoaded = MutableStateFlow(false)
    val libraryLoaded: StateFlow<Boolean> = _libraryLoaded

    private val _filteredSongs = MutableStateFlow(repository.filteredSongs)
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albums: StateFlow<List<AlbumItem>> = _albums

    private val _artists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val artists: StateFlow<List<ArtistItem>> = _artists

    private val _sortOptions = MutableStateFlow(SongSortScope.entries.associateWith(songSortStore::get))
    val sortOptions: StateFlow<Map<SongSortScope, SongSortOption>> = _sortOptions

    // ---------- UI 状态 ----------
    private val _selectedTab = MutableStateFlow(HomeTab.Songs)
    val selectedTab: StateFlow<HomeTab> = _selectedTab

    private val _screen = MutableStateFlow(AppScreen.Home)
    val screen: StateFlow<AppScreen> = _screen

    /** 当前打开的详情页（null = 在主页） */
    private val _detail = MutableStateFlow<DetailPage?>(null)
    val detail: StateFlow<DetailPage?> = _detail

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen

    private val _scanOpen = MutableStateFlow(false)
    val scanOpen: StateFlow<Boolean> = _scanOpen

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<SongSearchResult>>(emptyList())
    val searchResults: StateFlow<List<SongSearchResult>> = _searchResults

    private var searchJob: Job? = null

    // ---------- 播放状态（全局共享，主页 MiniPlayer 与播放器页同步） ----------
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    /** 播放模式（循环/单曲/随机），默认列表循环，不持久化（重启后回到循环） */
    private val _playMode = MutableStateFlow(PlayMode.RepeatAll)
    val playMode: StateFlow<PlayMode> = _playMode

    // ---------- 播放队列面板 ----------
    private val _queueOpen = MutableStateFlow(false)
    val queueOpen: StateFlow<Boolean> = _queueOpen

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex

    /** 队列同步签名：count + 当前位置 + 首尾 id + 本地变更版本号，没变就不重建列表 */
    private var queueSignature: String = ""

    /** 本地对队列的增删移都会递增它，让签名能感知队列中间的顺序变化 */
    private var queueVersion: Int = 0

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    /** 喜欢的歌曲 id 集合（持久化到 ListeningStore） */
    private val _likedIds = MutableStateFlow(listeningStore.likedIds)
    val likedIds: StateFlow<Set<Long>> = _likedIds

    // ---------- For you 页派生数据 ----------

    /** 有配套 .lrc 歌词的歌曲（Lyric Sync 卡片） */
    private val _lyricSongs = MutableStateFlow<List<Song>>(emptyList())
    val lyricSongs: StateFlow<List<Song>> = _lyricSongs

    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs

    /** Featured：精选 9 首（没听过/最少听的 6 首 + 常听的 3 首） */
    private val _featuredSongs = MutableStateFlow<List<Song>>(emptyList())
    val featuredSongs: StateFlow<List<Song>> = _featuredSongs

    /** Most Played：最近 30 天常听的歌曲，取前 32 */
    private val _mostPlayedSongs = MutableStateFlow<List<Song>>(emptyList())
    val mostPlayedSongs: StateFlow<List<Song>> = _mostPlayedSongs

    /** Recently played：最近播放过的所有歌曲（最近在前） */
    private val _recentSongs = MutableStateFlow<List<Song>>(emptyList())
    val recentSongs: StateFlow<List<Song>> = _recentSongs

    /** 每周收听 Top5：歌曲 + 播放次数（近 7 天） */
    private val _weeklyTop = MutableStateFlow<List<Pair<Song, Int>>>(emptyList())
    val weeklyTop: StateFlow<List<Pair<Song, Int>>> = _weeklyTop

    /** 本周收听总时长（ms） */
    private val _weeklyListeningMs = MutableStateFlow(0L)
    val weeklyListeningMs: StateFlow<Long> = _weeklyListeningMs

    private val _playlists = MutableStateFlow(playlistStore.playlists)
    val playlists: StateFlow<List<Playlist>> = _playlists

    /** For you 各卡片右上角的封面（从列表里挑第一首确实有封面的歌） */
    private val _cardCovers = MutableStateFlow(ForYouCovers())
    val cardCovers: StateFlow<ForYouCovers> = _cardCovers

    /** 封面探测签名：卡片列表没变就不重复扫文件 */
    private var coverSignature = ""

    /** 收听时长累计到该 tick 数时落盘一次（500ms × 30 = 15s） */
    private var listeningFlushTicks = 0

    private var controller: MediaController? = null
    /** mediaId -> Song 映射，用于从播放器回调还原曲目信息 */
    private val songByMediaId = mutableMapOf<String, Song>()

    init {
        connectController()
        startPositionTicker()
        // 冷启动同步读缓存（~80KB 小文件，主线程 <100ms），首帧直接渲染列表
        repository.readCachedSongs()?.let { cached ->
            if (cached.isNotEmpty()) applySongs(cached)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) loadSongs()
    }

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
        refreshRecentOnReturn()
    }

    fun setSongSort(scope: SongSortScope, option: SongSortOption) {
        songSortStore.set(scope, option)
        _sortOptions.value = _sortOptions.value + (scope to option)
    }

    fun openPlayer() {
        _screen.value = AppScreen.Player
    }

    fun closePlayer() {
        _screen.value = AppScreen.Home
        refreshRecentOnReturn()
    }

    fun openDetail(detail: DetailPage) {
        _detail.value = detail
    }

    fun closeDetail() {
        _detail.value = null
        refreshRecentOnReturn()
    }

    fun openSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchOpen.value = true
    }

    fun closeSearch() {
        searchJob?.cancel()
        _searchOpen.value = false
        refreshRecentOnReturn()
    }

    fun openScan() {
        _scanOpen.value = true
    }

    fun closeScan() {
        _scanOpen.value = false
        refreshRecentOnReturn()
    }

    /** 歌名结果立即展示，歌词文件在后台检索并追加，保证歌名命中始终排在歌词命中之前 */
    fun updateSearchQuery(rawQuery: String) {
        _searchQuery.value = rawQuery
        searchJob?.cancel()
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        val songs = _songs.value
        val titleResults = songs.filter { it.title.contains(query, ignoreCase = true) }
            .map { SongSearchResult(it) }
        _searchResults.value = titleResults
        val titleIds = titleResults.mapTo(mutableSetOf()) { it.song.id }
        searchJob = viewModelScope.launch {
            delay(200)
            val lyricResults = withContext(Dispatchers.IO) {
                songs.mapNotNull { song ->
                    if (song.id in titleIds) return@mapNotNull null
                    LyricsRepository.findMatchingLine(song, query)?.let { line ->
                        SongSearchResult(song, line)
                    }
                }
            }
            if (_searchQuery.value.trim() == query) {
                _searchResults.value = titleResults + lyricResults
            }
        }
    }

    fun onForeground() {
        refreshRecentOnReturn()
    }

    /** 加载媒体库：后台扫 MediaStore，有变化才更新；空结果不写缓存（防查询偶发失败冲掉好缓存） */
    fun loadSongs() {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch {
            try {
                val list = repository.querySongs()
                if (list != _songs.value) applySongs(list)
                if (list.isNotEmpty()) repository.saveCachedSongs(list)
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun applySongs(list: List<Song>) {
        val displayList = songTitleStore.apply(list)
        _songs.value = displayList
        _folders.value = repository.groupByFolder(displayList)
        _albums.value = repository.groupByAlbum(displayList)
        _artists.value = repository.groupByArtist(displayList)
        _libraryLoaded.value = true
        displayList.forEach { songByMediaId[it.id.toString()] = it }
        refreshForYou()
        // Featured 只在冷启动媒体库加载时刷新一次（进入 For you 页不再实时刷新）
        refreshFeatured()
        // 歌词文件探测走 IO（每首歌一次 exists 检查），不阻塞首帧
        viewModelScope.launch {
            _lyricSongs.value = withContext(Dispatchers.IO) {
                if (LyricsRepository.canReadLyrics()) displayList.filter { LyricsRepository.hasLrc(it) }
                else emptyList()
            }
            refreshCardCovers()
        }
    }

    fun renameSong(song: Song, displayTitle: String): Boolean {
        val title = displayTitle.trim()
        if (title.isEmpty()) return false
        songTitleStore.rename(song, title)
        applySongs(_songs.value)
        return true
    }

    fun originalSongTitle(song: Song): String = songTitleStore.originalTitle(song)

    // ---------- For you 页 ----------

    /**
     * 刷新 Featured 精选 9 首：
     * 没听过/播放量最少的 6 首 + 常听的 3 首；完全没播放记录时纯随机 9 首。
     * 各池内**有封面的优先**（IO 探测，每池最多扫 20 个候选）；本地歌曲不够时有几首算几首。
     */
    fun refreshFeatured() {
        val all = _songs.value
        if (all.isEmpty()) {
            _featuredSongs.value = emptyList()
            return
        }
        val byId = all.associateBy { it.id }
        val counts = listeningStore.allTimeCounts()
        viewModelScope.launch {
            _featuredSongs.value = withContext(Dispatchers.IO) {
                if (counts.isEmpty()) {
                    return@withContext all.shuffled().preferArtwork(9)
                }
                // 冷池：优先没听过的（随机），不够再补播放量最少的
                val unplayed = all.filter { (counts[it.id] ?: 0) == 0 }.shuffled()
                val leastPlayed = all.filter { (counts[it.id] ?: 0) > 0 }
                    .sortedBy { counts[it.id] }
                val cold = (unplayed + leastPlayed).preferArtwork(6)
                val coldIds = cold.map { it.id }.toSet()
                // 热池：常听前 3，避开已选入冷池的
                val hot = listeningStore.topCounts(0L)
                    .mapNotNull { (id, _) -> byId[id] }
                    .filter { it.id !in coldIds }
                    .preferArtwork(3)
                (cold + hot).take(9)
            }
        }
    }

    /** 有封面的优先：按原顺序探测（最多 20 个候选），有封面的排前面，保持相对顺序 */
    private fun List<Song>.preferArtwork(count: Int): List<Song> {
        val withArt = mutableListOf<Song>()
        val withoutArt = mutableListOf<Song>()
        for (song in take(20)) {
            if (SongArtworkFetcher.hasArtwork(getApplication(), song.artwork)) withArt += song
            else withoutArt += song
        }
        return (withArt + withoutArt).take(count)
    }

    /** 重算 For you 页除 Featured 外的派生数据（Featured 有独立刷新语义） */
    private fun refreshForYou() {
        val byId = _songs.value.associateBy { it.id }
        val now = System.currentTimeMillis()
        _likedSongs.value = _songs.value.filter { it.id in _likedIds.value }
        // For you 正在展示时保留最近播放快照，返回该页时再刷新，避免列表突然重排
        if (_selectedTab.value != HomeTab.ForYou) refreshRecentSongs()
        // Most Played：近 30 天常听前 32；近期无数据时退回全量统计
        val monthCounts = listeningStore.topCounts(now - 30L * 24 * 60 * 60 * 1000)
            .ifEmpty { listeningStore.topCounts(0L) }
        _mostPlayedSongs.value = monthCounts.mapNotNull { (id, _) -> byId[id] }.take(32)
        // 每周 Top5：近 7 天播放次数
        _weeklyTop.value = listeningStore.topCounts(now - 7L * 24 * 60 * 60 * 1000)
            .mapNotNull { (id, count) -> byId[id]?.let { it to count } }
            .take(5)
        _weeklyListeningMs.value = listeningStore.weeklyListeningMs(now)
        refreshCardCovers()
    }

    private fun refreshRecentOnReturn() {
        if (_selectedTab.value == HomeTab.ForYou &&
            _screen.value == AppScreen.Home &&
            _detail.value == null &&
            !_searchOpen.value &&
            !_scanOpen.value
        ) {
            refreshRecentSongs()
        }
    }

    /** 最近播放按最近一次播放时间倒序去重，最新播放的歌曲始终在第一项 */
    private fun refreshRecentSongs() {
        val byId = _songs.value.associateBy { it.id }
        _recentSongs.value = listeningStore.recentIds().mapNotNull { byId[it] }
    }

    /** 后台挑各卡片封面：每个列表取第一首确实有封面的歌（没封面就依次往后，最多探 10 首） */
    private fun refreshCardCovers() {
        val signature = buildString {
            listOf(_lyricSongs.value, _likedSongs.value, _mostPlayedSongs.value).forEach { list ->
                list.forEach { append(it.id).append(',') }
                append(';')
            }
            append(_songs.value.size)
        }
        if (signature == coverSignature) return
        coverSignature = signature
        viewModelScope.launch {
            _cardCovers.value = withContext(Dispatchers.IO) {
                ForYouCovers(
                    lyric = firstWithArtwork(_lyricSongs.value),
                    liked = firstWithArtwork(_likedSongs.value),
                    mostPlayed = firstWithArtwork(_mostPlayedSongs.value),
                    shuffle = firstWithArtwork(_songs.value),
                )
            }
        }
    }

    private fun firstWithArtwork(list: List<Song>): Song? =
        list.take(10).firstOrNull { SongArtworkFetcher.hasArtwork(getApplication(), it.artwork) }

    fun createPlaylist(name: String) {
        playlistStore.create(name)
        _playlists.value = playlistStore.playlists
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long): Boolean {
        val added = playlistStore.addSong(playlistId, songId)
        if (added) _playlists.value = playlistStore.playlists
        return added
    }

    fun createPlaylistAndAdd(name: String, songId: Long): Playlist {
        val playlist = playlistStore.create(name)
        playlistStore.addSong(playlist.id, songId)
        _playlists.value = playlistStore.playlists
        return playlist
    }

    fun createPlaylistAndAdd(name: String, songs: List<Song>): Playlist {
        val playlist = playlistStore.createWithSongs(name, songs.map { it.id })
        _playlists.value = playlistStore.playlists
        return playlist
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>): Int {
        val added = playlistStore.addSongs(playlistId, songIds)
        if (added > 0) _playlists.value = playlistStore.playlists
        return added
    }

    fun likeSongs(songIds: Set<Long>): Int {
        val added = listeningStore.addLikes(songIds)
        _likedIds.value = listeningStore.likedIds
        refreshForYou()
        return added
    }

    fun removeSongsFromPlaylist(playlistId: Long, songIds: Set<Long>) {
        playlistStore.removeSongs(playlistId, songIds)
        _playlists.value = playlistStore.playlists
    }

    fun reorderPlaylist(playlistId: Long, songIds: List<Long>) {
        playlistStore.reorder(playlistId, songIds)
        _playlists.value = playlistStore.playlists
    }

    /** 只从 App 与播放队列删除，真实音频文件保留；后续扫描按 id/path 过滤 */
    fun deleteSongsFromApp(songs: List<Song>) {
        if (songs.isEmpty()) return
        repository.filterSongs(songs)
        _filteredSongs.value = repository.filteredSongs
        val ids = songs.mapTo(mutableSetOf()) { it.id }
        playlistStore.removeSongsEverywhere(ids)
        _playlists.value = playlistStore.playlists
        controller?.let { player ->
            queueVersion++
            for (index in player.mediaItemCount - 1 downTo 0) {
                if (player.getMediaItemAt(index).mediaId.toLongOrNull() in ids) {
                    player.removeMediaItem(index)
                }
            }
        }
        applySongs(_songs.value.filterNot { it.id in ids })
        viewModelScope.launch { repository.saveCachedSongs(_songs.value) }
        if (_searchOpen.value) updateSearchQuery(_searchQuery.value)
    }

    fun restoreFilteredSong(song: Song) {
        repository.restoreFilteredSong(song)
        _filteredSongs.value = repository.filteredSongs
        loadSongs()
    }

    // ---------- 播放控制 ----------

    /** 从列表的指定位置开始播放整个列表（作为播放队列）；openPlayer = true 时同时拉起播放页（单曲点击场景） */
    fun playSongs(list: List<Song>, startIndex: Int, openPlayer: Boolean = false) {
        if (list.isEmpty()) return
        val c = controller ?: return
        queueVersion++
        list.forEach { songByMediaId[it.id.toString()] = it }
        c.setMediaItems(list.map { it.toMediaItem() }, startIndex.coerceIn(list.indices), 0L)
        // 从列表点歌：回到列表循环（即使之前是随机）
        applyPlayMode(PlayMode.RepeatAll)
        c.prepare()
        c.play()
        if (openPlayer) openPlayer()
    }

    /** Shuffle 按钮：开启随机并播放全部 */
    fun playShuffled(list: List<Song>) {
        if (list.isEmpty()) return
        val c = controller ?: return
        queueVersion++
        list.forEach { songByMediaId[it.id.toString()] = it }
        c.setMediaItems(list.map { it.toMediaItem() })
        applyPlayMode(PlayMode.Shuffle)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    /** 按进度比例跳转（进度环点击） */
    fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        val duration = c.duration
        if (duration > 0) c.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    // ---------- 播放模式（循环/单曲/随机） ----------

    fun cyclePlayMode() = applyPlayMode(_playMode.value.next())

    fun applyPlayMode(mode: PlayMode) {
        _playMode.value = mode
        controller?.let { c ->
            when (mode) {
                PlayMode.RepeatAll -> {
                    c.shuffleModeEnabled = false
                    c.repeatMode = Player.REPEAT_MODE_ALL
                }
                PlayMode.RepeatOne -> {
                    c.shuffleModeEnabled = false
                    c.repeatMode = Player.REPEAT_MODE_ONE
                }
                PlayMode.Shuffle -> {
                    c.repeatMode = Player.REPEAT_MODE_ALL
                    c.shuffleModeEnabled = true
                }
            }
        }
    }

    // ---------- 播放队列面板 ----------

    fun openQueue() {
        _queueOpen.value = true
    }

    fun closeQueue() {
        _queueOpen.value = false
    }

    /** 点队列里的某首：跳转并播放 */
    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    /** 从队列移除（移除当前播放项时 ExoPlayer 自动切下一首） */
    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        queueVersion++
        c.removeMediaItem(index)
    }

    /** 拖拽排序 */
    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount || from == to) return
        queueVersion++
        c.moveMediaItem(from, to)
    }

    /** 清空队列并停止播放 */
    fun clearQueue() {
        val c = controller ?: return
        queueVersion++
        c.stop()
        c.clearMediaItems()
        closeQueue()
        closePlayer()
    }

    fun toggleLike(songId: Long) {
        listeningStore.toggleLike(songId)
        _likedIds.value = listeningStore.likedIds
        refreshForYou()
    }

    // ---------- 定时关闭 ----------

    /** 定时关闭剩余毫秒数；null = 未开启 */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs

    private var sleepTimerJob: Job? = null

    /** 设置定时关闭：durationMs 后暂停播放（重复设置则替换）；传 0 取消 */
    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (durationMs <= 0L) {
            _sleepTimerRemainingMs.value = null
            return
        }
        sleepTimerJob = viewModelScope.launch {
            // 用本机时钟记截止时间，避免 delay 累计误差
            val endAt = SystemClock.elapsedRealtime() + durationMs
            while (true) {
                val remaining = endAt - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                _sleepTimerRemainingMs.value = remaining
                delay(1000)
            }
            _sleepTimerRemainingMs.value = null
            controller?.pause()
        }
    }

    // ---------- MediaController 连接 ----------

    private fun connectController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(playerListener)
            // 默认列表循环（修正 ExoPlayer 默认播完即停）；不恢复上次模式，重启后总是循环
            applyPlayMode(PlayMode.RepeatAll)
            syncFromController(c)
        }, ContextCompat.getMainExecutor(context))
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromController(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 记录播放事件：For you 的 Recently / Most Played / 每周 Top 以此为数据源。
            // 单曲循环的重复触发不算新的一次播放
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            val id = mediaItem?.mediaId?.toLongOrNull() ?: return
            listeningStore.recordPlay(id)
            refreshForYou()
        }
    }

    /** 把播放器状态同步到 StateFlow */
    private fun syncFromController(player: Player) {
        _isPlaying.value = player.isPlaying
        _playMode.value = when {
            player.shuffleModeEnabled -> PlayMode.Shuffle
            player.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.RepeatOne
            else -> PlayMode.RepeatAll
        }
        val mediaId = player.currentMediaItem?.mediaId
        _currentSong.value = mediaId?.let { songByMediaId[it] }
        val duration = player.duration
        _durationMs.value = if (duration > 0) duration else (_currentSong.value?.durationMs ?: 0L)
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        syncQueue(player)
    }

    /** 同步播放队列：只在队列结构或当前位置变化时重建，避免 seek 时整列表刷新 */
    private fun syncQueue(player: Player) {
        val count = player.mediaItemCount
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val signature = buildString {
            append(count).append('|').append(index).append('|').append(queueVersion)
            if (count > 0) {
                append('|').append(player.getMediaItemAt(0).mediaId)
                append('|').append(player.getMediaItemAt(count - 1).mediaId)
            }
        }
        if (signature == queueSignature) return
        queueSignature = signature
        _queueIndex.value = index
        _queue.value = (0 until count).mapNotNull { songByMediaId[player.getMediaItemAt(it).mediaId] }
    }

    /** 轮询播放进度；播放中顺便累计收听时长（每周收听统计的数据源） */
    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                        listeningStore.addListeningMs(500)
                        _weeklyListeningMs.value = listeningStore.weeklyListeningMs()
                        if (++listeningFlushTicks >= 30) {
                            listeningFlushTicks = 0
                            listeningStore.flush()
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(
                        // 通知栏封面走 MediaStore 专辑封面表
                        ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)
                    )
                    .build()
            )
            .build()

    override fun onCleared() {
        listeningStore.flush()
        controller?.release()
        controller = null
        super.onCleared()
    }
}
