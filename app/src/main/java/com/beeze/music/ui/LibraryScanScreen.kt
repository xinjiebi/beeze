package com.beeze.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 媒体库扫描与已过滤歌曲管理 */
@Composable
fun LibraryScanScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    BackHandler(onBack = viewModel::closeScan)

    Box(Modifier.fillMaxSize().background(BeezeColors.SurfaceSoft)) {
        Box(Modifier.fillMaxWidth().height(360.dp).topPurpleGradient())
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    modifier = Modifier.size(32.dp).iconClickable(onClick = viewModel::closeScan).padding(4.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Library scan", style = BeezeType.button, color = BeezeColors.Body)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Filtered (${filteredSongs.size})",
                    style = BeezeType.title,
                    color = BeezeColors.Body,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (scanning) "Scanning…" else "Scan now",
                    style = BeezeType.button,
                    color = if (scanning) BeezeColors.Muted else BeezeColors.Primary,
                    modifier = if (scanning) Modifier else Modifier.iconClickable(onClick = viewModel::loadSongs),
                )
            }
            Text(
                "Deleting from Beeze never deletes the local file. Filtered songs stay hidden during future scans and can be restored here.",
                style = BeezeType.bodySm,
                color = BeezeColors.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (filteredSongs.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No filtered songs", style = BeezeType.bodyMd, color = BeezeColors.Muted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    items(filteredSongs, key = { it.path }) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(song.artwork, 44.dp, RoundedCornerShape(8.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    song.title,
                                    style = BeezeType.button,
                                    color = BeezeColors.Body,
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
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Restore",
                                style = BeezeType.bodySm,
                                color = BeezeColors.Primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.iconClickable {
                                    viewModel.restoreFilteredSong(song)
                                    showToast(context, "Restored ${song.title}")
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
