package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beeze.music.R
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 自建歌单管理操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionsSheet(
    name: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BeezeColors.Surface,
        scrimColor = BeezeColors.Scrim,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(60.dp, 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BeezeColors.Muted)
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                name,
                style = BeezeType.button.copy(fontWeight = FontWeight.Bold),
                color = BeezeColors.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SongActionRow(R.drawable.ic_edit, tr(R.string.rename_playlist_action), onRename)
            SongActionRow(R.drawable.ic_queue_clear, tr(R.string.delete_playlist_action), onDelete)
        }
    }
}
