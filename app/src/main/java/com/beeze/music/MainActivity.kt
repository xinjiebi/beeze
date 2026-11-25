package com.beeze.music

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.Coil
import coil.ImageLoader
import com.beeze.music.data.AppLocale
import com.beeze.music.data.AppearanceMode
import com.beeze.music.data.SongArtworkFetcher
import com.beeze.music.data.SongSortOption
import com.beeze.music.data.SongSortScope
import com.beeze.music.data.sortedForDisplay
import com.beeze.music.ui.HomeScreen
import com.beeze.music.ui.adInsertionIndices
import com.beeze.music.ui.DetailScreen
import com.beeze.music.ui.LibraryScanScreen
import com.beeze.music.ui.LyricThemes
import com.beeze.music.ui.PlayerContentCache
import com.beeze.music.ui.PlayerScreen
import com.beeze.music.ui.QueueSheet
import com.beeze.music.ui.SearchScreen
import com.beeze.music.ui.tr
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeLightColors
import com.beeze.music.ui.theme.BeezeTheme
import com.beeze.music.ui.theme.BeezeType
import com.syncedlyrics.sdk.SyncedLyricsView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val STARTUP_AD_WAIT_MS = 2_000L
private const val APP_OPEN_WAIT_MS = 8_000L
private const val ADS_INIT_WAIT_MS = 5_000L

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private val launchPendingState = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BeezeApplication
        val adsManager = app.adsManager
        val coldStart = !app.appOpenHandledForProcess
        app.appOpenHandledForProcess = true
        val hasPermission = hasAudioPermission()
        val offerAppOpen = coldStart && hasPermission && adsManager.canOfferAppOpen()
        launchPendingState.value = coldStart && hasPermission
        lifecycleScope.launch {
            try {
                if (coldStart && hasPermission) {
                    val prepared = withTimeoutOrNull(ADS_INIT_WAIT_MS) {
                        adsManager.prepareAds(this@MainActivity)
                    } == true
                    if (prepared) {
                        val nativeKeys = startupNativeAdKeys()
                        val widthDp = (resources.configuration.screenWidthDp - 40).coerceAtLeast(1)
                        adsManager.preloadStartupAds(nativeKeys, widthDp)
                        coroutineScope {
                            launch {
                                if (offerAppOpen) {
                                    val ad = withTimeoutOrNull(APP_OPEN_WAIT_MS) { adsManager.loadAppOpenAd() }
                                    if (ad != null && !isFinishing && !isDestroyed) {
                                        adsManager.showAppOpenAd(this@MainActivity, ad)
                                    }
                                } else {
                                    adsManager.preloadAppOpenAd()
                                }
                            }
                            withTimeoutOrNull(STARTUP_AD_WAIT_MS) { adsManager.awaitStartupAds(nativeKeys) }
                            adsManager.completeStartupPreload()
                        }
                    }
                } else {
                    withTimeoutOrNull(3_000L) { adsManager.prepareAds(this@MainActivity) }
                    if (hasPermission) adsManager.preloadAppOpenAd()
                }
            } finally {
                launchPendingState.value = false
            }
        }
        // 注册封面加载器：专辑封面表 + 内嵌封面两步兜底
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components { add(SongArtworkFetcher.Factory(this@MainActivity)) }
                .build()
        )
        // 透明沉浸式状态栏 + 深色图标
        enableEdgeToEdge()
        // 部分 ROM（如 MIUI）即使导航栏透明也会强制绘制白色手势条底板，需显式关闭对比度衬底
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        window.navigationBarColor = BeezeLightColors.Transparent.toArgb()
        val darkTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        setContent {
            val appearance by viewModel.appearanceMode.collectAsStateWithLifecycle()
            val darkTheme = when (appearance) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            BeezeTheme(darkTheme = darkTheme) {
                BeezeApp(viewModel, darkTheme, launchPendingState.value)
            }
        }
    }

    private fun startupNativeAdKeys(): Set<String> {
        val songs = viewModel.songs.value
        if (songs.isEmpty()) return emptySet()
        val sortOption = viewModel.sortOptions.value[SongSortScope.Songs] ?: SongSortOption.Default
        val sortedSongs = songs.sortedForDisplay(sortOption)
        val songIndices = adInsertionIndices(sortedSongs.size, 7, sortedSongs.hashCode())
        val albums = viewModel.albums.value
        val albumIndices = adInsertionIndices(albums.size, 8, albums.hashCode())
        val artists = viewModel.artists.value
        val artistIndices = adInsertionIndices(artists.size, 11, artists.hashCode())
        val keys = linkedSetOf<String>()
        keys += "home_for_you"
        songIndices.firstOrNull()?.let { keys += "home_songs_$it" }
        albumIndices.firstOrNull()?.let { keys += "home_albums_$it" }
        artistIndices.firstOrNull()?.let { keys += "home_artists_$it" }
        if (viewModel.likedSongs.value.isNotEmpty()) keys += "detail_liked"
        if (viewModel.recentSongs.value.isNotEmpty()) keys += "detail_recent"
        viewModel.playlists.value.forEach { playlist ->
            if (playlist.songIds.isNotEmpty()) keys += "detail_playlist_${playlist.id}"
        }
        songIndices.drop(1).forEach { keys += "home_songs_$it" }
        albumIndices.drop(1).forEach { keys += "home_albums_$it" }
        artistIndices.drop(1).forEach { keys += "home_artists_$it" }
        return keys
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStop() {
        super.onStop()
        if (hasAudioPermission()) {
            (application as BeezeApplication).adsManager.preloadAppOpenAd()
        }
    }
}

/** 根组合：按权限与界面状态切换主页、搜索、扫描管理、详情、播放器与权限引导 */
@Composable
fun BeezeApp(viewModel: PlayerViewModel, darkTheme: Boolean, launchPending: Boolean) {
    val context = LocalContext.current
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    DisposableEffect(darkTheme) {
        val activity = context as ComponentActivity
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        onDispose { }
    }
    val detailPage by viewModel.detail.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val scanOpen by viewModel.scanOpen.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.playbackError.collect { title ->
            com.beeze.music.ui.showToast(
                context,
                context.getString(R.string.playback_failed, title),
            )
        }
    }

    // 原生歌词 View 全局复用；无歌词封面仍由 Beeze 现有布局绘制。
    val lyricView = remember {
        SyncedLyricsView(context.applicationContext).apply {
            lyricStyle = LyricThemes.current(context)
            backgroundStyle = LyricThemes.currentBackground(context)
            emptyLyricsCoverEnabled = false
        }
    }
    val playerContentCache = remember { PlayerContentCache() }

    // 音乐扫描只申请普通媒体权限；歌词目录由用户在关于页单独授权。
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // 通知权限只影响后台播放通知的展示，拒绝不阻塞主流程（fire-and-forget）
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var showPermissionSettings by remember { mutableStateOf(false) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
        showPermissionSettings = !granted &&
            !(context as ComponentActivity).shouldShowRequestPermissionRationale(audioPermission)
        if (granted && Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 启动时与每次回到前台都重新检查音乐权限。
    val lifecycleOwner = LocalLifecycleOwner.current
    var initialPermissionCheck by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onForeground()
                val granted = ContextCompat.checkSelfPermission(
                    context, audioPermission
                ) == PackageManager.PERMISSION_GRANTED
                if (granted != viewModel.hasPermission.value) {
                    viewModel.onPermissionResult(granted, initialCheck = initialPermissionCheck)
                }
                initialPermissionCheck = false
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        launchPending -> AppLaunchScreen()
        !hasPermission -> PermissionGuide(
            showSettings = showPermissionSettings,
            onRequest = { audioPermissionLauncher.launch(audioPermission) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            },
        )
        scanOpen -> LibraryScanScreen(viewModel)
        detailPage != null -> DetailScreen(viewModel, detailPage!!)
        searchOpen -> SearchScreen(viewModel)
        else -> HomeScreen(viewModel)
    }

    val queueOpen by viewModel.queueOpen.collectAsStateWithLifecycle()

    // 播放器使用独立原生窗口，整体从底部拉起；下层主页保持原状态
    PlayerOverlay(
        visible = !launchPending && screen == AppScreen.Player,
        darkTheme = darkTheme,
        onDismiss = viewModel::closePlayer,
    ) {
        Box(Modifier.fillMaxSize()) {
            PlayerScreen(
                viewModel = viewModel,
                lyricView = lyricView,
                contentCache = playerContentCache,
                onBack = viewModel::closePlayer,
            )
            if (queueOpen) QueueSheet(viewModel)
        }
    }

    // 非播放页的播放列表面板由主页窗口承接
    if (!launchPending && queueOpen && screen != AppScreen.Player) {
        QueueSheet(viewModel)
    }
}

/** 由系统窗口动画统一移动歌词背景和 Compose 控件，避免两层绘制错位。 */
@Composable
private fun PlayerOverlay(
    visible: Boolean,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(darkTheme) {
        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent { BeezeTheme(darkTheme = darkTheme) { content() } }
        }
        val dialog = Dialog(activity, R.style.BeezePlayerDialog).apply {
            setContentView(composeView)
            setOnDismissListener { onDismiss() }
        }
        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, composeView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        onDispose {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            dialog.dismiss()
        }
    }
}

@Composable
private fun AppLaunchScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(BeezeColors.SurfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "Beeze",
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Beeze", style = BeezeType.displayMd, color = BeezeColors.Ink)
        }
    }
}

/** 未授权时的引导 UI */
@Composable
private fun PermissionGuide(
    showSettings: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BeezeColors.SurfaceSoft)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                tr(R.string.permission_title),
                style = BeezeType.displayMd,
                color = BeezeColors.Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                tr(if (showSettings) R.string.permission_settings_message else R.string.permission_message),
                style = BeezeType.bodyMd,
                color = BeezeColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (showSettings) {
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BeezeColors.Primary),
                ) {
                    Text(tr(R.string.open_settings), style = BeezeType.button, color = BeezeColors.Surface)
                }
            } else {
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BeezeColors.Primary),
                ) {
                    Text(tr(R.string.authorize), style = BeezeType.button, color = BeezeColors.Surface)
                }
            }
        }
    }
}
