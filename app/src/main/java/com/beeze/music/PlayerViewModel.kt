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
import com.beeze.music.data.MediaStoreRepository
import com.beeze.music.data.Song
import com.beeze.music.player.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

/** 二级详情页：文件夹 / 专辑 / 艺术家 */
sealed interface DetailPage {
    data class Folder(val folder: FolderItem) : DetailPage
    data class Album(val album: AlbumItem) : DetailPage
    data class Artist(val artist: ArtistItem) : DetailPage
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application)

    // ---------- 权限与媒体库 ----------
    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    /** 媒体库是否已完成首次加载（缓存或扫描）。空态文案只在加载完成后才显示，避免闪烁 */
    private val _libraryLoaded = MutableStateFlow(false)
    val libraryLoaded: StateFlow<Boolean> = _libraryLoaded

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albums: StateFlow<List<AlbumItem>> = _albums

    private val _artists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val artists: StateFlow<List<ArtistItem>> = _artists

    // ---------- UI 状态 ----------
    private val _selectedTab = MutableStateFlow(HomeTab.Songs)
    val selectedTab: StateFlow<HomeTab> = _selectedTab

    private val _screen = MutableStateFlow(AppScreen.Home)
    val screen: StateFlow<AppScreen> = _screen

    /** 当前打开的详情页（null = 在主页） */
    private val _detail = MutableStateFlow<DetailPage?>(null)
    val detail: StateFlow<DetailPage?> = _detail

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

    /** 喜欢的歌曲 id 集合（内存态） */
    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedIds: StateFlow<Set<Long>> = _likedIds

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
    }

    fun openPlayer() {
        _screen.value = AppScreen.Player
    }

    fun closePlayer() {
        _screen.value = AppScreen.Home
    }

    fun openDetail(detail: DetailPage) {
        _detail.value = detail
    }

    fun closeDetail() {
        _detail.value = null
    }

    /** 加载媒体库：后台扫 MediaStore，有变化才更新；空结果不写缓存（防查询偶发失败冲掉好缓存） */
    fun loadSongs() {
        viewModelScope.launch {
            val list = repository.querySongs()
            if (list != _songs.value) applySongs(list)
            if (list.isNotEmpty()) repository.saveCachedSongs(list)
        }
    }

    private fun applySongs(list: List<Song>) {
        _songs.value = list
        _folders.value = repository.groupByFolder(list)
        _albums.value = repository.groupByAlbum(list)
        _artists.value = repository.groupByArtist(list)
        _libraryLoaded.value = true
        list.forEach { songByMediaId[it.id.toString()] = it }
    }

    // ---------- 播放控制 ----------

    /** 从列表的指定位置开始播放整个列表（作为播放队列） */
    fun playSongs(list: List<Song>, startIndex: Int) {
        if (list.isEmpty()) return
        val c = controller ?: return
        queueVersion++
        list.forEach { songByMediaId[it.id.toString()] = it }
        c.setMediaItems(list.map { it.toMediaItem() }, startIndex.coerceIn(list.indices), 0L)
        // 从列表点歌：回到列表循环（即使之前是随机）
        applyPlayMode(PlayMode.RepeatAll)
        c.prepare()
        c.play()
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
        _likedIds.value = _likedIds.value.let {
            if (songId in it) it - songId else it + songId
        }
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

    /** 轮询播放进度 */
    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isPlaying) {
                        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
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
        controller?.release()
        controller = null
        super.onCleared()
    }
}
