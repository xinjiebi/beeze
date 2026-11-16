# SyncedLyricsSDK Android 接入说明

## 1. 功能与职责

SyncedLyricsSDK 是基于 Android 原生 View 和 Canvas 的歌词展示组件，提供八种歌词样式、封面背景、无歌词封面以及部分样式的歌词浏览。当前项目为 Android 版本，不包含 iOS SDK。

宿主 App 负责播放器、歌曲信息、网络请求、歌词加载状态、封面加载、页面布局和播放控制。SDK 不播放音频，不请求数据，也不会在拖动歌词时修改播放进度。SDK 的公开 View 接口均须在主线程调用。

## 2. 环境与集成

- Android `minSdk 24`；当前项目 `compileSdk 34`。
- 使用 JDK 17 构建；项目使用 Android Gradle Plugin 8.5.2、Kotlin 1.9.24。
- SDK 没有播放器或 WebView 依赖；宿主需提供兼容的 Kotlin 标准库。AAR 不会自动携带本地依赖的传递信息。

### 源码模块接入

将 SDK 项目作为独立模块放入或映射到宿主工程，示例使用 Gradle Groovy DSL：

```groovy
// 宿主 settings.gradle；路径按实际位置调整
include ':syncedlyrics'
project(':syncedlyrics').projectDir = file('../SyncedLyricsSDK')
```

```groovy
// 宿主 app/build.gradle
dependencies {
    implementation project(':syncedlyrics')
}
```

宿主工程需配置可访问 Google、Maven Central 和 Gradle Plugin Portal 的仓库，并配置兼容的 Android 与 Kotlin 插件版本。如果 SDK 目录与宿主工程相邻关系不同，请调整 `projectDir`。SDK 项目自身的 `settings.gradle` 不会自动并入宿主配置。

### AAR 接入

在 SDK 根目录执行：

```bash
./gradlew testDebugUnitTest assembleRelease
```

构建成功后，从 `build/outputs/aar/` 获取 release AAR，复制到宿主 `app/libs/`，然后按实际文件名引用：

```groovy
dependencies {
    implementation files('libs/SyncedLyricsSDK-release.aar')
    // 如宿主未包含 Kotlin 标准库，需额外声明兼容版本。
}
```

首次构建可能需要下载 Gradle、Android 构建工具和依赖；请在联网且配置好 JDK 与 Android SDK 的环境执行。旧的 AAR 不一定包含当前源码的修改，交付前应重新构建并验证。

## 3. 创建 View

```xml
<!-- activity_player.xml；歌曲信息和播放控件由宿主添加为其他子 View -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.syncedlyrics.sdk.SyncedLyricsView
        android:id="@+id/lyricsView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</FrameLayout>
```

也可以使用 `SyncedLyricsView(context)` 动态创建。SDK 默认绘制铺满 View 的封面背景；宿主可以把歌名、歌手和播放控件叠放在它上面。状态栏、导航栏、挖孔屏、横竖屏以及控件避让由宿主通过布局和 WindowInsets 处理。

## 4. 传入歌词

### 普通或增强 LRC

```kotlin
lyricsView.setLrc("""
    [00:01.00]第一行歌词
    [00:04.00]<00:04.00>你<00:04.50>好
""".trimIndent())
```

支持行时间标签、连续多时间标签、单独一行的 `[offset:毫秒]` 和 `<mm:ss.xx>` 逐词时间标签。小数支持 1～3 位。增强 LRC 的最后一个词默认持续到下一行；最后一行没有下一行时，最后一个词的结束时间等于开始时间。如需指定精确结束时间或翻译，请传结构化歌词。

### 结构化歌词

```kotlin
import com.syncedlyrics.sdk.LyricLine
import com.syncedlyrics.sdk.LyricWord

lyricsView.setLyrics(listOf(
    LyricLine(
        startMs = 1_000L,
        text = "你好",
        translation = "Hello",
        words = listOf(
            LyricWord(text = "你", startMs = 1_000L, endMs = 1_500L),
            LyricWord(text = "好", startMs = 1_500L, endMs = 2_200L)
        )
    ),
    LyricLine(startMs = 3_000L, text = "下一行")
))
```

所有时间都是歌曲时间轴上的**绝对毫秒数**。`words` 的文本需按顺序出现在对应的 `text` 中。SDK 按开始时间排序；同一时间的多行以排序后的最后一行为当前行。没有逐词时间时，各样式按自身规则展示；需要进度渲染的样式以当前行到下一行的时长估算，最后一行默认使用 4 秒。

歌词格式不被支持时，请由 App 转换为上述结构。歌词加载中、无歌词和加载失败应由 App 区分；SDK 的空列表表示当前没有可展示的歌词。

## 5. 与播放器同步

```kotlin
// 在播放器回调中调用；positionMs 是播放器当前实际位置，单位为毫秒。
lyricsView.setPlaybackState(
    positionMs = positionMs,
    playing = isPlaying,
    playbackSpeed = speed // 默认为 1f，必须为非负有限数
)
```

在播放、暂停、拖动进度（seek）、切歌、倍速变化、缓冲状态变化等影响实际播放的时刻及时同步；播放期间建议每 250～1000ms 使用播放器真实位置校准一次。View 在两次更新之间自行推算位置，所以不需要 App 每帧更新。播放器暂停或缓冲时应传入真实的非播放状态，以冻结画面。

```kotlin
lyricsView.setLyricOffsetMs(200L)  // 正值让歌词提前 200ms，负值让歌词延后
```

LRC 的 `[offset:...]` 是解析时的时间戳偏移，正值会推迟 LRC 时间，与此接口的正值含义相反。切歌时及时更新封面、歌词和播放状态；异步加载需由 App 避免旧歌曲的结果覆盖新歌曲。

## 6. 歌词和背景样式

```kotlin
import com.syncedlyrics.sdk.LyricStyle
import com.syncedlyrics.sdk.BackgroundStyle

lyricsView.lyricStyle = LyricStyle.CAPPELLA
lyricsView.backgroundStyle = BackgroundStyle.LIQUID
lyricsView.activeColor = 0xFFBE8BFF.toInt()
lyricsView.inactiveColor = 0x64FFFFFF
lyricsView.translationColor = 0x96FFFFFF.toInt()
lyricsView.textSizeSp = 27f // 必须大于 0 且为有限数
```

歌词样式：`CLASSIC` 经典、`PARTITA` 竖排、`CAPPELLA` 对话、`TILT` 倾斜、`DIORAMA` 纵深、`MONET` 海报、`PENDOLO` 钟摆、`STILL` 静谧。默认是 `CLASSIC`。纵深为二维透视近似。

背景样式：`LIQUID` 封面流动（默认）、`MOSAIC` 圆点马赛克、`OIL` 油画。三者独立于歌词样式；流动背景随播放暂停而停止。SDK 按需生成并缓存当前封面的背景样式，首次生成油画或马赛克目前在主线程进行，切歌及切换样式的耗时应在目标设备验证。

对话、海报、钟摆、静谧可拖动浏览歌词，松手 3 秒后自动回到播放行；对话只显示已经出现的歌词。倾斜不支持拖动。可设置 `lyricsView.manualScrollEnabled = false` 关闭浏览；浏览不会 seek，也没有内置点击歌词跳转回调。

## 7. 封面与无歌词状态

```kotlin
import com.syncedlyrics.sdk.EmptyLyricsCoverStyle

lyricsView.setCover(songCoverBitmap) // 当前歌曲的 Bitmap；传 null 清除并使用 SDK 默认背景
lyricsView.emptyLyricsCoverStyle = EmptyLyricsCoverStyle.SQUARE_RECORD // 默认：方形封面 + 黑胶
// 或：EmptyLyricsCoverStyle.CIRCLE，圆形封面旋转
lyricsView.setEmptyLyricsCoverPlaceholder(placeholderBitmap) // 可选；null 恢复 SDK 默认前景图
lyricsView.emptyLyricsCoverEnabled = true
lyricsView.setLyrics(emptyList()) // 确认无歌词时
```

仅歌词列表为空时出现无歌词封面；有歌词但播放尚未到第一句，不触发该状态。前景优先使用歌曲封面，其次是宿主占位图，最后使用 SDK 默认前景图。背景优先使用歌曲封面；没有时使用 SDK 默认背景封面。前景占位图不改变背景。方形样式的黑胶旋转、方形封面静止；圆形样式的封面旋转，暂停时停止。设置 `emptyLyricsCoverEnabled = false` 后，空状态前景由宿主处理。

`setCover` 和 `setEmptyLyricsCoverPlaceholder` 接收 Android `Bitmap`，不直接解析 SVG；宿主可将资源转换或渲染为 Bitmap 后传入。调用方管理 Bitmap 生命周期，SDK 使用期间不要回收它。传入已回收的 Bitmap 会抛出异常。调用 `lyricsView.coverBackgroundEnabled = false` 可关闭 SDK 背景绘制；这不关闭无歌词前景封面。

**布局限制：**当前 SDK 自动决定封面位置和尺寸，尚未提供自定义封面坐标、边界查询或布局回调。App 可以叠加歌曲信息，但无法通过公开 API 精确读取封面的实际边界；需要此能力时应先扩展 SDK，再依靠实际边界布局，不能使用尚不存在的接口。

## 8. 生命周期与验证

宿主负责恢复播放状态并在页面重新可见时向 View 同步播放器实际位置。若 Activity 因旋转重建，需由宿主恢复选择的样式和播放器状态；SDK 不管理宿主持久化。屏幕切换、遮挡和后台播放规则由 App 决定。

运行演示与测试：

```bash
./gradlew testDebugUnitTest :demo:assembleDebug
```

演示 APK 位于 `demo/build/outputs/apk/debug/`，使用模拟播放时间，不播放音频。设置中可选择歌词、背景、无歌词封面样式，并通过“无歌词预览”和“有歌曲封面”检查空状态。

上线前请在目标机型验证：长歌词及英文换行、切歌和异步加载、暂停/缓冲/seek、手机与平板横竖屏、系统栏避让、无歌词和无封面，以及各背景下的帧率和内存。当前无障碍体验及所有设备兼容性仍需按业务场景验证。
