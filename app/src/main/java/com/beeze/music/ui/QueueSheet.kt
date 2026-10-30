package com.beeze.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.PlayMode
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType
import kotlin.math.roundToInt

/** 面板行内图标色（拖拽把手 / 删除 / 更多） */
private val QueueIconTint = Color(0xFFE5E5E0)

/**
 * 播放列表面板（Figma 87:1724）：底部弹层，高度由内容支撑（标题行 + N 首歌曲行），
 * 最小为可用高度的 35%，最大 75%。
 * 顶部拖拽把 60x2；标题行左侧「N songs」，右侧 随机/清空/添加；
 * 歌曲行 60dp：长按左侧把手拖拽排序（拖动时只移动视觉，松手才一次性提交 move），
 * 右侧 X 从队列移除、⋮ 更多（占位）。从 MiniPlayer 与播放页右下角列表按钮拉起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(viewModel: PlayerViewModel) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val queueIndex by viewModel.queueIndex.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = viewModel::closeQueue,
        containerColor = BeezeColors.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(60.dp, 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(BeezeColors.Hairline)
                )
            }
        },
    ) {
        // 高度由内容支撑，最小 35%（避免歌曲很少时面板过矮），上限维持原设计的 75%；
        // 列表 weight(fill=false) 让内容不足时面板随内容收缩
        BoxWithConstraints {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight * 0.35f, max = maxHeight * 0.75f),
            ) {
            // 标题行：48dp，左「N songs」，右 随机 / 清空 / 添加（24dp 图标，间距 16dp）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${queue.size} songs",
                    style = BeezeType.bodySm,
                    color = BeezeColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                Image(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = "随机播放",
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable {
                            viewModel.applyPlayMode(PlayMode.Shuffle)
                            showToast(context, "随机播放")
                        },
                )
                Spacer(Modifier.width(16.dp))
                Image(
                    painter = painterResource(R.drawable.ic_queue_clear),
                    contentDescription = "清空播放列表",
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable {
                            viewModel.clearQueue()
                            showToast(context, "已清空播放列表")
                        },
                )
                Spacer(Modifier.width(16.dp))
                Image(
                    painter = painterResource(R.drawable.ic_queue_add),
                    contentDescription = "添加歌曲",
                    modifier = Modifier
                        .size(24.dp)
                        .iconClickable { showToast(context, "添加歌曲（占位）") },
                )
            }

            // 打开时滚到当前播放位置（上面多露两首）
            LaunchedEffect(Unit) {
                if (queue.isNotEmpty()) {
                    listState.scrollToItem((queueIndex - 2).coerceAtLeast(0))
                }
            }

            // 拖拽状态：from = 起手位置，offset = 累计位移；松手时一次性提交
            val rowHeightPx = with(LocalDensity.current) { 60.dp.toPx() }
            var dragFrom by remember { mutableIntStateOf(-1) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val haptics = LocalHapticFeedback.current
            // 预览落点（拖拽中随行变化）
            val dragTarget = if (dragFrom >= 0) {
                (dragFrom + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, queue.lastIndex)
            } else -1

            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                itemsIndexed(queue, key = { index, _ -> index }) { index, song ->
                    val isCurrent = index == queueIndex
                    // 其他行给被拖拽的行让位
                    val shiftPx = when {
                        dragFrom < 0 || index == dragFrom -> 0f
                        dragTarget > dragFrom && index in (dragFrom + 1)..dragTarget -> -rowHeightPx
                        dragTarget in 0..<dragFrom && index in dragTarget..<dragFrom -> rowHeightPx
                        else -> 0f
                    }
                    QueueRow(
                        title = song.title,
                        artist = song.artist,
                        isCurrent = isCurrent,
                        modifier = Modifier
                            .zIndex(if (index == dragFrom) 1f else 0f)
                            .graphicsLayer {
                                translationY =
                                    if (index == dragFrom) dragOffset else shiftPx
                            },
                        onClick = { viewModel.playQueueIndex(index) },
                        onRemove = { viewModel.removeQueueItem(index) },
                        onMore = { showToast(context, "更多（占位）") },
                        dragHandle = Modifier.iconDragHandle(
                            onDragStart = {
                                dragFrom = index
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { delta -> dragOffset += delta },
                            onDragEnd = {
                                if (dragFrom >= 0 && dragTarget >= 0 && dragTarget != dragFrom) {
                                    viewModel.moveQueueItem(dragFrom, dragTarget)
                                }
                                dragFrom = -1
                                dragOffset = 0f
                            },
                        ),
                    )
                }
            }
            }
        }
    }
}

/** 长按把手触发的垂直拖拽手势；位移通过 onDrag 累加，提交在 onDragEnd 统一做 */
private fun Modifier.iconDragHandle(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onDragStart() },
        onDrag = { change, amount ->
            change.consume()
            onDrag(amount.y)
        },
        onDragEnd = onDragEnd,
        onDragCancel = onDragEnd,
    )
}

/** 队列歌曲行（60dp）：左拖拽把手，中标题+艺术家，右 X 移除 + ⋮ 更多 */
@Composable
private fun QueueRow(
    title: String,
    artist: String,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMore: () -> Unit,
    dragHandle: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(BeezeColors.Surface)
            .clickable(onClick = onClick) // 列表整行保留波纹（项目约定）
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_queue_move),
            contentDescription = "拖拽排序",
            colorFilter = ColorFilter.tint(QueueIconTint),
            modifier = Modifier
                .size(32.dp)
                .then(dragHandle)
                .padding(4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = BeezeType.bodySm.copy(fontWeight = FontWeight.Medium),
                color = if (isCurrent) BeezeColors.Primary else BeezeColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                artist,
                style = BeezeType.caption,
                color = BeezeColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_queue_remove),
            contentDescription = "从队列移除",
            colorFilter = ColorFilter.tint(QueueIconTint),
            modifier = Modifier
                .size(32.dp)
                .iconClickable(onClick = onRemove)
                .padding(4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Image(
            painter = painterResource(R.drawable.ic_more),
            contentDescription = "更多",
            colorFilter = ColorFilter.tint(QueueIconTint),
            modifier = Modifier
                .size(32.dp)
                .iconClickable(onClick = onMore)
                .padding(4.dp),
        )
    }
}
