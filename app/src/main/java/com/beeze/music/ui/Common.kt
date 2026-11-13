package com.beeze.music.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.beeze.music.R
import com.beeze.music.data.SongArtwork
import com.beeze.music.ui.theme.BeezeColors
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 顶部紫色渐变（约 162deg，0% rgba(161,53,255,0.15) → 58% 透明） */
fun Modifier.topPurpleGradient(): Modifier = drawBehind {
    val brush = Brush.linearGradient(
        0f to BeezeColors.Primary.copy(alpha = 0.15f),
        0.58f to BeezeColors.Primary.copy(alpha = 0f),
        start = Offset.Zero,
        end = Offset(size.width * 0.31f, size.height * 0.95f),
    )
    drawRect(brush)
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** 图标点击：去掉波纹背景色，改为马达震动反馈 */
@Composable
fun Modifier.iconClickable(onClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
    )
}

/**
 * 打开「所有文件访问」设置页（歌词 .lrc 读取需要）。
 * 各 ROM 对包名直达支持不一（MIUI 等会直接打不开），逐级降级：
 * 包名直达 → 权限列表页 → 应用详情页，保证用户至少能找到入口。
 */
fun openAllFilesAccessSettings(context: Context) {
    val intents = listOf(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // 当前 ROM 不支持该入口，尝试下一级
        }
    }
}

/** mm:ss 格式化 */
fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** 列表默认封面占位：primary-subtle 圆角底 + CD 图标（对应 Figma 6:573，图标约占 32/44） */
@Composable
fun CoverPlaceholder(size: Dp, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(BeezeColors.PrimarySubtle),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_cd),
            contentDescription = null,
            modifier = Modifier.size(size * 0.727f),
        )
    }
}

/** 播放页默认封面：与列表同一个默认图（Figma 6:573 的 CD 图标），圆形裁切 */
@Composable
fun PlayerCoverPlaceholder(size: Dp) {
    CoverPlaceholder(size, CircleShape)
}

/**
 * 专辑封面：加载成功显示封面（Crop 裁剪不变形），加载中/无封面/加载失败显示占位。
 * placeholder 为空时用列表默认占位（CD 图标版）。
 */
@Composable
fun CoverImage(
    artwork: SongArtwork?,
    size: Dp,
    shape: Shape = RoundedCornerShape(8.dp),
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
) {
    // 占位分支同样应用外部 modifier（对齐/边距不能丢，否则调用方布局会错位）
    if (artwork == null) {
        Box(modifier) { if (placeholder != null) placeholder() else CoverPlaceholder(size, shape) }
        return
    }
    val context = LocalContext.current
    var failed = remember(artwork) { androidx.compose.runtime.mutableStateOf(false) }
    if (failed.value) {
        Box(modifier) { if (placeholder != null) placeholder() else CoverPlaceholder(size, shape) }
        return
    }
    // 占位图垫在图片下层：加载期间显示占位而不是白底，封面加载好后淡入盖住
    Box(modifier.size(size).clip(shape)) {
        if (placeholder != null) placeholder() else CoverPlaceholder(size, shape)
        AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(artwork)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed.value = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 播放中封面的缓慢旋转角度；暂停时停在当前角度 */
@Composable
fun rememberCoverRotation(isPlaying: Boolean): androidx.compose.runtime.State<Float> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 每 20 秒一圈，无限循环
            while (true) {
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 20000, easing = LinearEasing),
                )
            }
        } else {
            angle.stop()
        }
    }
    return angle.asState()
}

fun Modifier.rotate(degrees: Float): Modifier = graphicsLayer { rotationZ = degrees }

/**
 * 页面纵向橡皮筋拉伸：内容不足一屏无法滚动时，竖直拖拽按比例平移+拉伸页面，松手回弹。
 * 子级列表会优先消费滚动与平台 overscroll，本效果只接管嵌套滚动链里剩余的增量，
 * 所以可滚动页面的行为不变，两者不会叠加。
 */
@Composable
fun Modifier.pageStretch(): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // 拉伸上限：阻尼渐近到这个值，不会无限拉
    val maxStretch = with(LocalDensity.current) { 130.dp.toPx() }
    val releaseSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val connection = remember(maxStretch) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val y = available.y
                val cur = offset.value
                // 已拉伸时反向拖动：先把拉伸量收回来，剩余增量留给子级
                if (cur == 0f || y * cur >= 0f) return Offset.Zero
                val new = cur + y
                return if (new * cur >= 0f) {
                    scope.launch { offset.snapTo(new) }
                    Offset(0f, y)
                } else {
                    scope.launch { offset.snapTo(0f) }
                    Offset(0f, -cur)
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val y = available.y
                if (y == 0f) return Offset.Zero
                // 子级消化不了的增量转成拉伸量，越拉阻尼越大
                scope.launch {
                    val resistance = 1f - (abs(offset.value) / maxStretch).coerceIn(0f, 1f)
                    offset.snapTo(offset.value + y * 0.65f * resistance)
                }
                return Offset(0f, y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offset.value != 0f) offset.animateTo(0f, releaseSpec)
                return Velocity.Zero
            }
        }
    }
    return this
        .nestedScroll(connection)
        // 只观察不消费：所有手指抬起后回弹
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
                if (offset.value != 0f) scope.launch { offset.animateTo(0f, releaseSpec) }
            }
        }
        .graphicsLayer {
            val o = offset.value
            if (o != 0f) {
                translationY = o
                scaleY = 1f + (abs(o) / size.height).coerceAtMost(0.2f) * 0.5f
                // 从被拉的那条边开始拉伸：下拉定住顶边，上拉定住底边
                transformOrigin = TransformOrigin(0.5f, if (o > 0f) 0f else 1f)
            }
        }
}

/**
 * 不可滚动的居中静态页（占位 / 空态）：verticalScroll 让拖动增量进入嵌套滚动链，
 * 从而能被外层 pageStretch 接管产生拉伸；内容不足一屏时不会真的滚动
 */
@Composable
fun StaticPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** 圆形艺术家占位：44dp 圆底 + 33dp 弱版蜜蜂（设计比例 33/44） */
@Composable
fun ArtistAvatarPlaceholder(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(BeezeColors.PrimarySubtle),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_bee_soft),
            contentDescription = null,
            modifier = Modifier.size(size * 0.75f),
        )
    }
}
