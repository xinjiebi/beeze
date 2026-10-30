package com.beeze.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.ImageLoader
import com.beeze.music.data.SongArtworkFetcher
import com.beeze.music.ui.HomeScreen
import com.beeze.music.ui.DetailScreen
import com.beeze.music.ui.LyricThemes
import com.beeze.music.ui.openAllFilesAccessSettings
import com.folialyric.FoliaLyricView
import com.beeze.music.ui.PlayerScreen
import com.beeze.music.ui.QueueSheet
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeTheme
import com.beeze.music.ui.theme.BeezeType
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            BeezeTheme {
                BeezeApp(viewModel)
            }
        }
    }
}

/** 根组合：按权限与界面状态切换 主页 / 播放器页 / 权限引导 */
@Composable
fun BeezeApp(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val detailPage by viewModel.detail.collectAsStateWithLifecycle()

    // Folia 歌词背景层：全局一个实例（样式设置存在 localStorage）。
    // WebView 首次创建要加载 WebView Provider（主线程几百毫秒），同步创建会拖慢冷启动首帧，
    // 所以等首帧上屏后再创建；只有进入播放页才会用到它，那时早已就绪
    var lyricView by remember { mutableStateOf<FoliaLyricView?>(null) }
    LaunchedEffect(Unit) {
        delay(500) // 让首页首帧先渲染
        val view = FoliaLyricView(context.applicationContext).apply { touchThrough = true }
        // JS 桥就绪后隐藏 SDK 内置控制条（控制统一走 Beeze UI），并应用持久化的歌词样式
        view.setControlsVisible(false)
        view.setVisualizerMode(LyricThemes.current(context))
        // 背景用 monet（封面模糊派生）：默认的 latent 带半调点阵层，封面偏灰时显得脏
        view.setBackground(LyricThemes.currentBackground(context))
        lyricView = view
    }
    DisposableEffect(lyricView) {
        // 显式捕获当前值：委托属性的闭包会读到「最新值」，key 变化时会把新 view 误 release
        val view = lyricView
        onDispose { view?.release() }
    }

    // 授权策略：API 30+ 直接引导开启「所有文件访问」——一个权限同时覆盖音频扫描与
    // .lrc 歌词读取，不再先弹音频权限再接力跳设置页；API 30 以下走 READ_EXTERNAL_STORAGE 弹窗
    val needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    // 通知权限只影响后台播放通知的展示，拒绝不阻塞主流程（fire-and-forget）
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    // API 30 以下的存储权限弹窗
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    // 启动时与每次回到前台都重新检查授权（用户可能刚从设置页开完开关返回）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = if (needsAllFiles) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
                if (granted != viewModel.hasPermission.value) {
                    viewModel.onPermissionResult(granted)
                    if (granted && Build.VERSION.SDK_INT >= 33) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !hasPermission -> PermissionGuide(
            onRequest = {
                if (needsAllFiles) {
                    openAllFilesAccessSettings(context)
                } else {
                    legacyStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        )
        screen == AppScreen.Player -> lyricView?.let {
            PlayerScreen(
                viewModel = viewModel,
                lyricView = it,
                onBack = viewModel::closePlayer,
            )
        }
        detailPage != null -> DetailScreen(viewModel, detailPage!!)
        else -> HomeScreen(viewModel)
    }

    // 播放列表面板：全局弹层，MiniPlayer 与播放页的列表按钮都能拉起
    val queueOpen by viewModel.queueOpen.collectAsStateWithLifecycle()
    if (queueOpen) {
        QueueSheet(viewModel)
    }
}

/** 未授权时的引导 UI */
@Composable
private fun PermissionGuide(onRequest: () -> Unit) {
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
                "需要「所有文件」访问权限",
                style = BeezeType.displayMd,
                color = BeezeColors.Ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "用于扫描本机音乐与歌词（.lrc）文件。\n" +
                    "点击按钮跳转系统设置，打开「允许访问所有文件」开关后返回即可。",
                style = BeezeType.bodyMd,
                color = BeezeColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeezeColors.Primary),
            ) {
                Text("去授权", style = BeezeType.button, color = BeezeColors.Surface)
            }
        }
    }
}
