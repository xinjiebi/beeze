package com.beeze.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.Song
import com.beeze.music.data.SongSearchResult
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType

/** 本地音乐搜索页（Figma 176:3713 / 176:3894） */
@Composable
fun SearchScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeSearch() {
        keyboardController?.hide()
        viewModel.closeSearch()
    }

    fun submitSearch() {
        if (query.isBlank()) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    BackHandler(onBack = ::closeSearch)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BeezeColors.SurfaceSoft)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconClickable(onClick = ::closeSearch),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(BeezeColors.Hairline)
                    .focusRequester(focusRequester)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true,
                textStyle = BeezeType.bodyMd.copy(color = BeezeColors.Ink),
                cursorBrush = SolidColor(BeezeColors.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Search Local Music",
                                style = BeezeType.bodyMd,
                                color = BeezeColors.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                "Search",
                style = BeezeType.button,
                color = BeezeColors.Primary,
                modifier = Modifier.clickable(onClick = ::submitSearch),
            )
        }

        val resultSongs = results.map { it.song }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(results, key = { _, result -> result.song.id }) { index, result ->
                SearchResultRow(
                    result = result,
                    query = query.trim(),
                    onClick = {
                        keyboardController?.hide()
                        viewModel.playSongs(resultSongs, index, openPlayer = true)
                    },
                    onMore = {
                        keyboardController?.hide()
                        actionSong = result.song
                    },
                )
            }
        }
    }
    actionSong?.let { song ->
        SongActionsSheet(viewModel, song, onDismiss = { actionSong = null })
    }
}

@Composable
private fun SearchResultRow(
    result: SongSearchResult,
    query: String,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val song = result.song
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(song.artwork, 44.dp, RoundedCornerShape(8.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                highlightedText(song.title, query),
                style = BeezeType.button,
                color = BeezeColors.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                highlightedText(result.lyricLine ?: song.artist, query),
                style = BeezeType.caption,
                color = BeezeColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_more),
            contentDescription = "更多",
            modifier = Modifier
                .size(24.dp)
                .iconClickable(onClick = onMore),
        )
    }
}

private fun highlightedText(text: String, query: String): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)
    val matches = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text)
    val builder = AnnotatedString.Builder()
    var cursor = 0
    matches.forEach { match ->
        builder.append(text.substring(cursor, match.range.first))
        builder.pushStyle(SpanStyle(color = BeezeColors.Primary))
        builder.append(match.value)
        builder.pop()
        cursor = match.range.last + 1
    }
    builder.append(text.substring(cursor))
    return builder.toAnnotatedString()
}
