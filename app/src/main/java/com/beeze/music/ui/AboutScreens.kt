package com.beeze.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.beeze.music.DetailPage
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.LyricsRepository
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 关于页：应用信息、扫描与权限管理、隐私政策入口。 */
@Composable
fun AboutScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    BackHandler(onBack = viewModel::closeDetail)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        AboutTopBar("About", viewModel::closeDetail)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = "Beeze 图标",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text("Beeze", style = BeezeType.displayMd, color = BeezeColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Version $versionName",
                style = BeezeType.bodySm,
                color = BeezeColors.Muted,
            )
            Spacer(Modifier.height(24.dp))
            AboutActionRow(
                title = "Library scan",
                subtitle = "扫描本地音乐，管理已过滤歌曲",
                actionLabel = "Manage",
                onClick = viewModel::openScan,
            )
            Spacer(Modifier.height(12.dp))
            val lyricsGranted = LyricsRepository.canReadLyrics()
            AboutActionRow(
                title = "Lyrics file access",
                subtitle = if (lyricsGranted) "已授权读取歌曲同目录下的 .lrc 文件" else "读取 .lrc 歌词需要「所有文件访问」权限",
                actionLabel = if (lyricsGranted) "Granted" else "Authorize",
                onClick = if (lyricsGranted) null else ({ openAllFilesAccessSettings(context) }),
            )
            Spacer(Modifier.height(12.dp))
            AboutActionRow(
                title = "Privacy Policy",
                subtitle = "了解权限、数据存储和删除规则",
                actionLabel = "Read",
                onClick = { viewModel.openDetail(DetailPage.Privacy) },
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "本地音乐，只在这台设备上使用",
                style = BeezeType.caption,
                color = BeezeColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 当前版本的隐私政策，内容需与实际权限和网络行为保持一致。 */
@Composable
fun PrivacyScreen(viewModel: PlayerViewModel) {
    val backToAbout = { viewModel.openDetail(DetailPage.About) }
    BackHandler(onBack = backToAbout)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AboutTopBar("Privacy Policy", backToAbout)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "更新日期：2026年11月16日",
                style = BeezeType.caption,
                color = BeezeColors.Muted,
            )
            PrivacySection(
                "我们处理哪些数据",
                "Beeze 是本地音乐播放器，无需注册账号。歌曲、歌词、封面、播放记录、喜欢列表、自建歌单和扫描过滤记录仅用于提供本地播放与管理功能。当前版本未接入广告或行为统计 SDK。",
            )
            PrivacySection(
                "数据是否上传",
                "当前版本未声明 INTERNET 网络权限，不会通过网络上传你的歌曲、歌词、封面、播放记录、歌单或其他个人数据。上述应用数据保存在本机，Android 系统云备份也已关闭。",
            )
            PrivacySection(
                "媒体与文件权限",
                "音频读取权限用于扫描、展示和播放本地音频。「所有文件访问」仅用于读取歌曲同目录下的 .lrc 歌词文件；该权限覆盖范围较大，但你拒绝后仍可使用不包含本地歌词读取的功能。",
            )
            PrivacySection(
                "播放与通知权限",
                "前台媒体播放服务用于保持后台播放，通知权限用于显示播放状态和控制按钮。通知不是必须权限，拒绝后不影响应用内播放操作。",
            )
            PrivacySection(
                "删除与恢复",
                "在 Beeze 中删除本地歌曲只会将其加入应用的扫描过滤列表，不会删除设备上的真实音频文件。你可以在 Library scan 页面恢复过滤。删除自建歌单不会删除其中的歌曲。卸载应用会清除本机应用数据。",
            )
            PrivacySection(
                "后续功能变化",
                "如果后续版本需要联网上传数据，我们会在功能上线前更新本政策，明确上传目的、数据范围、保存期限和接收方，并在需要时重新征得你的同意，不会在未告知的情况下上传。",
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AboutTopBar(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "返回",
                modifier = Modifier
                    .size(32.dp)
                    .iconClickable(onClick = onBack)
                    .padding(4.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(title, style = BeezeType.button, color = BeezeColors.Body)
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BeezeColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = BeezeType.button, color = BeezeColors.Body)
            Text(subtitle, style = BeezeType.caption, color = BeezeColors.Muted)
        }
        Spacer(Modifier.size(12.dp))
        Text(actionLabel, style = BeezeType.caption, color = BeezeColors.Primary)
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = BeezeType.button, color = BeezeColors.Body)
        Text(body, style = BeezeType.bodySm, color = BeezeColors.Muted)
    }
}
