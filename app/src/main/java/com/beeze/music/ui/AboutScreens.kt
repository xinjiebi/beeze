package com.beeze.music.ui

import androidx.activity.ComponentActivity
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.beeze.music.DetailPage
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.AppLanguage
import com.beeze.music.data.AppLocale
import com.beeze.music.data.AppearanceMode
import com.beeze.music.data.LyricsRepository
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 关于页：外观、应用语言、权限管理与隐私政策入口。 */
@Composable
fun AboutScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val appearanceMode by viewModel.appearanceMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    var showAppearance by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var pendingLanguage by remember { mutableStateOf<AppLanguage?>(null) }
    BackHandler(onBack = viewModel::closeDetail)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AboutTopBar(tr(R.string.about), viewModel::closeDetail)
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
                contentDescription = tr(R.string.beeze_app_icon),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text("Beeze", style = BeezeType.displayMd, color = BeezeColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                tr(R.string.version_format, versionName),
                style = BeezeType.bodySm,
                color = BeezeColors.Muted,
            )
            Spacer(Modifier.height(24.dp))
            AboutActionRow(
                title = tr(R.string.appearance),
                subtitle = tr(R.string.appearance_subtitle),
                actionLabel = tr(
                    when (appearanceMode) {
                        AppearanceMode.SYSTEM -> R.string.follow_system
                        AppearanceMode.LIGHT -> R.string.light_mode
                        AppearanceMode.DARK -> R.string.dark_mode
                    }
                ),
                onClick = { showAppearance = true },
            )
            Spacer(Modifier.height(12.dp))
            AboutActionRow(
                title = tr(R.string.language),
                subtitle = tr(R.string.language_subtitle),
                actionLabel = when (appLanguage) {
                    AppLanguage.SIMPLIFIED_CHINESE -> tr(R.string.language_chinese)
                    AppLanguage.ENGLISH -> tr(R.string.language_english)
                    AppLanguage.SYSTEM -> tr(
                        if (AppLocale.isSimplifiedChinese(context)) R.string.language_chinese
                        else R.string.language_english
                    )
                },
                onClick = { showLanguage = true },
            )
            Spacer(Modifier.height(12.dp))
            val lyricsGranted = LyricsRepository.canReadLyrics()
            AboutActionRow(
                title = tr(R.string.lyrics_file_access),
                subtitle = tr(if (lyricsGranted) R.string.lyrics_access_granted else R.string.lyrics_access_required),
                actionLabel = tr(if (lyricsGranted) R.string.granted else R.string.authorize),
                onClick = if (lyricsGranted) null else ({ openAllFilesAccessSettings(context) }),
            )
            Spacer(Modifier.height(12.dp))
            AboutActionRow(
                title = tr(R.string.privacy_policy),
                subtitle = tr(R.string.privacy_subtitle),
                actionLabel = tr(R.string.read),
                onClick = { viewModel.openDetail(DetailPage.Privacy) },
            )
            Spacer(Modifier.height(24.dp))
            Text(
                tr(R.string.local_music_only),
                style = BeezeType.caption,
                color = BeezeColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAppearance) {
        SettingsChoiceSheet(
            title = tr(R.string.appearance),
            options = listOf(
                AppearanceMode.SYSTEM to tr(R.string.follow_system),
                AppearanceMode.LIGHT to tr(R.string.light_mode),
                AppearanceMode.DARK to tr(R.string.dark_mode),
            ),
            selected = appearanceMode,
            onSelect = { mode ->
                viewModel.setAppearanceMode(mode)
                showAppearance = false
            },
            onDismiss = { showAppearance = false },
        )
    }
    if (showLanguage) {
        SettingsChoiceSheet(
            title = tr(R.string.language),
            options = listOf(
                AppLanguage.SIMPLIFIED_CHINESE to tr(R.string.language_chinese),
                AppLanguage.ENGLISH to tr(R.string.language_english),
            ),
            selected = if (appLanguage == AppLanguage.SYSTEM) {
                if (AppLocale.isSimplifiedChinese(context)) AppLanguage.SIMPLIFIED_CHINESE else AppLanguage.ENGLISH
            } else {
                appLanguage
            },
            onSelect = { language ->
                showLanguage = false
                pendingLanguage = language
            },
            onDismiss = { showLanguage = false },
        )
    }
    pendingLanguage?.let { language ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BeezeColors.Surface)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = BeezeColors.Primary,
                )
                Text(
                    tr(R.string.language_switching),
                    style = BeezeType.bodyMd,
                    color = BeezeColors.Body,
                )
            }
        }
        LaunchedEffect(language) {
            viewModel.setAppLanguage(language)
            (context as? ComponentActivity)?.recreate()
        }
    }
}

@Composable
internal fun AboutTopBar(title: String, onBack: (() -> Unit)?) {
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
                contentDescription = tr(R.string.back),
                colorFilter = themeIconTint(),
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
