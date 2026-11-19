package com.beeze.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.ui.theme.BeezeColors
import com.beeze.music.ui.theme.BeezeType
import kotlin.math.abs

/** 滚轮单项高度 44，一屏 5 项 */
private val WheelItemHeight = 44.dp
private val WheelHeight = WheelItemHeight * 5

/**
 * 定时关闭面板（底部弹层，与播放列表/歌词效果同款样式）：
 * 高度由内容支撑，双滚轮选择时长（Hours 0-12，Minutes 0-50 步进 10，默认 0 Hours 30 Minutes）。
 * 单位标签固定不滚动，只有数字滚；选中数字用主题色，其余淡色。
 * 右上角「OK」启动或替换定时；选 0 Hours 0 Minutes 即取消定时。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 分钟档位：0/10/20/30/40/50，默认 30（index 3）
    val minuteSteps = remember { (0..50 step 10).toList() }
    var hourIndex by remember { mutableIntStateOf(0) }
    var minuteIndex by remember { mutableIntStateOf(3) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        // 高度由内容支撑（标题行 + 滚轮 + 底部留白），上限与队列面板一致
        BoxWithConstraints {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.75f)) {
                // 标题行（48dp）：左侧标题，右侧确定
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr(R.string.sleep_timer),
                        style = BeezeType.bodySm,
                        color = BeezeColors.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        tr(R.string.ok),
                        style = BeezeType.button,
                        color = BeezeColors.Primary,
                        modifier = Modifier.iconClickable {
                            val hours = hourIndex
                            val minutes = minuteSteps[minuteIndex]
                            viewModel.setSleepTimer((hours * 60 + minutes) * 60_000L)
                            val message = when {
                                hours > 0 && minutes > 0 ->
                                    context.getString(R.string.timer_set_hours_minutes, hours, minutes)
                                hours > 0 -> context.getString(R.string.timer_set_hours, hours)
                                minutes > 0 -> context.getString(R.string.timer_set_minutes, minutes)
                                else -> context.getString(R.string.timer_off)
                            }
                            showToast(context, message)
                            onDismiss()
                        },
                    )
                }

                // 双滚轮：Hours（0-12）+ Minutes（0-50 步进 10）；单位固定，数字滚动
                Box(Modifier.fillMaxWidth().height(WheelHeight)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        WheelWithUnit(
                            items = (0..12).toList(),
                            unit = tr(R.string.hours),
                            initialIndex = hourIndex,
                            onSelectedChange = { hourIndex = it },
                            modifier = Modifier.weight(1f),
                        )
                        WheelWithUnit(
                            items = minuteSteps,
                            unit = tr(R.string.minutes),
                            initialIndex = minuteIndex,
                            onSelectedChange = { minuteIndex = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 选中区域上下两条指示线
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(1.dp)
                            .align(Alignment.Center)
                            .offset(y = -WheelItemHeight / 2)
                            .background(BeezeColors.Hairline)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(1.dp)
                            .align(Alignment.Center)
                            .offset(y = WheelItemHeight / 2)
                            .background(BeezeColors.Hairline)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 数字滚轮 + 固定单位标签：标签与选中行同高，不随滚动移动 */
@Composable
private fun WheelWithUnit(
    items: List<Int>,
    unit: String,
    initialIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(WheelHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberWheel(
            items = items,
            initialIndex = initialIndex,
            onSelectedChange = onSelectedChange,
            modifier = Modifier.width(52.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(unit, style = BeezeType.bodyMd, color = BeezeColors.Muted)
    }
}

/**
 * 单列数字滚轮：上下各垫 2 项高度让首尾项能滚到中心；
 * 距离中心最近的项为选中项（主题色），滚动停止后吸附回正。
 */
@Composable
private fun NumberWheel(
    items: List<Int>,
    initialIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 初始项即为 firstVisibleItem：item 顶对齐在 contentPadding 之后，恰好居中
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }
                ?.index ?: initialIndex
        }
    }
    // 选中变化实时上报
    LaunchedEffect(centerIndex) { onSelectedChange(centerIndex) }
    // 滚动停止后吸附到最近项（snap fling 之外的兜底）
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && centerIndex in items.indices) {
            listState.animateScrollToItem(centerIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.height(WheelHeight),
        contentPadding = PaddingValues(vertical = WheelItemHeight * 2),
        flingBehavior = rememberSnapFlingBehavior(listState),
    ) {
        itemsIndexed(items) { index, item ->
            val selected = index == centerIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$item",
                    style = if (selected) BeezeType.title else BeezeType.bodyMd,
                    color = if (selected) BeezeColors.Primary else BeezeColors.Muted,
                )
            }
        }
    }
}
