package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.Song
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

@Composable
fun RenameSongDialog(viewModel: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember(song) { mutableStateOf(song.title) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun save() {
        if (viewModel.renameSong(song, name)) {
            showToast(context, context.getString(R.string.display_name_updated))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BeezeColors.Surface,
        title = { Text(tr(R.string.rename_in_beeze), style = BeezeType.title, color = BeezeColors.Body) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BeezeColors.Canvas)
                        .padding(12.dp),
                    singleLine = true,
                    textStyle = BeezeType.bodyMd.copy(color = BeezeColors.Body),
                    cursorBrush = SolidColor(BeezeColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (name.isEmpty()) {
                                Text(tr(R.string.display_name), style = BeezeType.bodyMd, color = BeezeColors.Muted)
                            }
                            innerTextField()
                        }
                    },
                )
                Text(
                    tr(R.string.rename_display_explanation),
                    style = BeezeType.bodySm,
                    color = BeezeColors.Muted,
                )
            }
        },
        confirmButton = {
            Text(
                tr(R.string.save),
                style = BeezeType.button,
                color = if (name.isBlank()) BeezeColors.Muted else BeezeColors.Primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).then(
                    if (name.isBlank()) Modifier else Modifier.clickable { save() }
                ),
            )
        },
        dismissButton = {
            Text(
                tr(R.string.cancel),
                style = BeezeType.button,
                color = BeezeColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onDismiss),
            )
        },
    )
}

@Composable
fun SongInfoDialog(viewModel: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val originalTitle = viewModel.originalSongTitle(song)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BeezeColors.Surface,
        title = { Text(tr(R.string.song_info), style = BeezeType.title, color = BeezeColors.Body) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SongInfoItem(tr(R.string.display_name), song.title)
                if (originalTitle != song.title) SongInfoItem(tr(R.string.original_name), originalTitle)
                SongInfoItem(tr(R.string.artist), song.artist)
                SongInfoItem(tr(R.string.album), song.album)
                SongInfoItem(tr(R.string.duration), formatTime(song.durationMs))
                SongInfoItem(tr(R.string.file_path), song.path)
            }
        },
        confirmButton = {
            Text(
                tr(R.string.close),
                style = BeezeType.button,
                color = BeezeColors.Primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onDismiss),
            )
        },
    )
}

@Composable
private fun SongInfoItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = BeezeType.caption, color = BeezeColors.Muted)
        Text(value, style = BeezeType.bodySm, color = BeezeColors.Body)
    }
}
