package com.syncedlyrics.sdk

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.*

/** Native lyric renderer. Call public methods on the main thread. */
class SyncedLyricsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private data class ClassicItem(val offset: Int, val text: String, val width: Float, val timing: LongRange?)
    private data class ClassicPrepared(val items: List<ClassicItem>, val rows: List<List<Int>>, val fontSize: Float, val lineHeight: Float, val gap: Float, val scale: Float)
    private data class Row(val line: LyricLine, val layout: StaticLayout, val translation: StaticLayout?, val top: Float, val height: Float, val classic: ClassicPrepared?)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val meshColumns = 14
    private val meshRows = 14
    private val meshPoints = FloatArray((meshColumns + 1) * (meshRows + 1) * 2)
    private var cover: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.synced_lyrics_default_cover)
    private var songCover: Bitmap? = null
    private var emptyCoverPlaceholder: Bitmap? = null
    private val defaultEmptyCover by lazy { BitmapFactory.decodeResource(resources, R.drawable.synced_lyrics_empty_cover) }
    private val vinylRecord by lazy { BitmapFactory.decodeResource(resources, R.drawable.synced_lyrics_vinyl) }
    private var foregroundShaderImage: Bitmap? = null
    private var foregroundShader: BitmapShader? = null
    private val foregroundMatrix = Matrix()
    private var blurred: Bitmap? = null
    private var oilCover: Bitmap? = null
    private var mosaicCover: Bitmap? = null
    private val accent: Int get() = activeColor
    private var lines = emptyList<LyricLine>()
    private var rows = emptyList<Row>()
    private var dirty = true
    private var basePosition = 0L
    private var baseClock = 0L
    private var playing = false
    private var speed = 1f
    private var offset = 0L
    private var scroll = 0f
    private var lastFrame = 0L
    private var browsing = false
    private var dragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val resumeFollowing = Runnable { browsing = false; snap = false; lastFrame = 0L; invalidate() }
    /** Allow dragging supported lyric styles to browse without changing playback. */
    var manualScrollEnabled = true
        set(value) { field = value; if (!value) cancelBrowsing() }
    private fun supportsManualScroll() = lyricStyle in setOf(LyricStyle.CAPPELLA, LyricStyle.MONET, LyricStyle.PENDOLO, LyricStyle.STILL)
    private fun cancelBrowsing() {
        removeCallbacks(resumeFollowing); browsing = false; dragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    override fun onDetachedFromWindow() { cancelBrowsing(); super.onDetachedFromWindow() }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!manualScrollEnabled || !supportsManualScroll() || rows.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { touchDownX = event.x; touchDownY = event.y; lastTouchY = event.y; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - touchDownY) > touchSlop && abs(event.y - touchDownY) > abs(event.x - touchDownX)) {
                    dragging = true; browsing = true; snap = false; removeCallbacks(resumeFollowing)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    val time = positionAt(SystemClock.elapsedRealtime()) + offset
                    val last = if (lyricStyle == LyricStyle.CAPPELLA) rows.indexOfLast { it.line.startMs <= time } else rows.lastIndex
                    val end = rows.getOrNull(last)?.let { it.top + if (lyricStyle == LyricStyle.PENDOLO) it.height / 2f else 0f } ?: 0f
                    val start = rows.first().let { it.top + if (lyricStyle == LyricStyle.PENDOLO) it.height / 2f else 0f }
                    scroll = (scroll - (event.y - lastTouchY)).coerceIn(start, max(start, end))
                    invalidate()
                }
                lastTouchY = event.y; return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) { dragging = false; parent?.requestDisallowInterceptTouchEvent(false); removeCallbacks(resumeFollowing); postDelayed(resumeFollowing, 3000) }
                else if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return true
    }
    private var snap = true
    private var stateSet = false
    private var backgroundElapsedMs = 0L
    private var backgroundStartedMs = 0L
    var lyricStyle = LyricStyle.CLASSIC
        set(value) { cancelBrowsing(); field = value; dirty = true; snap = true; invalidate() }
    /** Background appearance, independent of lyricStyle. */
    var backgroundStyle = BackgroundStyle.LIQUID
        set(value) { field = value; prepareBackground(); invalidate() }
    /** Show the song cover or host placeholder when the lyric list is empty. */
    var emptyLyricsCoverEnabled = true
        set(value) { field = value; invalidate() }
    var emptyLyricsCoverStyle = EmptyLyricsCoverStyle.SQUARE_RECORD
        set(value) { field = value; invalidate() }
    var coverBackgroundEnabled = true
        set(value) { field = value; invalidate() }
    var activeColor = Color.rgb(190, 139, 255)
        set(value) { field = value; invalidate() }
    var inactiveColor = Color.argb(100, 255, 255, 255)
        set(value) { field = value; invalidate() }
    var translationColor = Color.argb(150, 255, 255, 255)
        set(value) { field = value; invalidate() }
    var textSizeSp = 27f
        set(value) { require(value > 0f && value.isFinite()); field = value; dirty = true; invalidate() }

    init { updateCover() }
    fun setCover(bitmap: Bitmap?) {
        require(bitmap == null || !bitmap.isRecycled) { "Cover bitmap has been recycled" }
        songCover = bitmap
        cover = bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.synced_lyrics_default_cover)
        updateCover(); invalidate()
    }
    /** Placeholder for the empty-lyrics foreground; does not change the background. */
    fun setEmptyLyricsCoverPlaceholder(bitmap: Bitmap?) {
        require(bitmap == null || !bitmap.isRecycled) { "Placeholder bitmap has been recycled" }
        emptyCoverPlaceholder = bitmap
        invalidate()
    }
    private fun drawEmptyLyricsCover(canvas: Canvas, animationTime: Long) {
        if (!emptyLyricsCoverEnabled) return
        val image = songCover?.takeUnless { it.isRecycled } ?: emptyCoverPlaceholder?.takeUnless { it.isRecycled } ?: defaultEmptyCover ?: return
        val record = vinylRecord ?: return
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0).toFloat()
        val shortDp = min(availableWidth, availableHeight) / resources.displayMetrics.density
        val phonePortrait = height > width && shortDp < 600f
        val size = min(availableWidth * if (phonePortrait) .75f else .64f, availableHeight / 3f * 1.56f)
        if (size <= 0f) return
        val left = paddingLeft + (availableWidth - size) / 2f
        val centerY = paddingTop + availableHeight / 3f
        if (emptyLyricsCoverStyle == EmptyLyricsCoverStyle.CIRCLE) {
            val shader = if (foregroundShaderImage === image && foregroundShader != null) foregroundShader!! else {
                BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                    foregroundShaderImage = image; foregroundShader = it
                }
            }
            val centerX = left + size / 2f
            val scale = size / min(image.width, image.height)
            foregroundMatrix.setScale(scale, scale)
            foregroundMatrix.postTranslate(centerX - image.width * scale / 2f, centerY - image.height * scale / 2f)
            foregroundMatrix.postRotate((animationTime % 20000L) * 360f / 20000f, centerX, centerY)
            shader.setLocalMatrix(foregroundMatrix)
            paint.color = Color.WHITE; paint.shader = shader
            canvas.drawCircle(centerX, centerY, size / 2f, paint)
            paint.shader = null
            return
        }
        val squareSize = size * .91f
        val top = centerY - squareSize / 2f
        val recordSize = squareSize * .96f
        val recordLeft = left + size - recordSize
        val recordTop = centerY - recordSize / 2f
        canvas.save()
        canvas.rotate((animationTime % 20000L) * 360f / 20000f, recordLeft + recordSize / 2f, centerY)
        rect.set(recordLeft, recordTop, recordLeft + recordSize, recordTop + recordSize)
        paint.shader = null; paint.color = Color.WHITE
        canvas.drawBitmap(record, null, rect, paint)
        canvas.restore()
        val scale = squareSize / min(image.width, image.height)
        val shader = if (foregroundShaderImage === image && foregroundShader != null) foregroundShader!! else {
            BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                foregroundShaderImage = image; foregroundShader = it
            }
        }
        foregroundMatrix.setScale(scale, scale)
        foregroundMatrix.postTranslate(left + (squareSize - image.width * scale) / 2f, top + (squareSize - image.height * scale) / 2f)
        shader.setLocalMatrix(foregroundMatrix)
        paint.shader = shader
        rect.set(left, top, left + squareSize, top + squareSize)
        canvas.drawRoundRect(rect, squareSize * .05f, squareSize * .05f, paint)
        paint.shader = null
    }
    private fun updateCover() {
        blurred = null; oilCover = null; mosaicCover = null
        foregroundShaderImage = null; foregroundShader = null
        prepareBackground()
    }
    // Only build the selected background; cache other styles when first selected.
    private fun prepareBackground() {
        val image = cover?.takeUnless { it.isRecycled } ?: return
        when (backgroundStyle) {
            BackgroundStyle.LIQUID -> if (blurred == null) {
                val ratio = min(1f, 18f / min(image.width, image.height))
                val w = (image.width * ratio).toInt().coerceIn(1, 48)
                val h = (image.height * ratio).toInt().coerceIn(1, 48)
                val tiny = Bitmap.createScaledBitmap(image, w, h, true)
                val medium = Bitmap.createScaledBitmap(tiny, w * 8, h * 8, true)
                if (tiny !== image && tiny !== medium) tiny.recycle()
                blurred = medium
            }
            BackgroundStyle.OIL -> if (oilCover == null) oilCover = createOilCover(image)
            BackgroundStyle.MOSAIC -> if (mosaicCover == null) mosaicCover = createDotMosaic(image)
        }
    }

    /** Sample the cover into fine round tiles, cached until the artwork changes. */
    private fun createDotMosaic(image: Bitmap): Bitmap {
        val scale = min(1f, 132f / max(image.width, image.height))
        val cols = (image.width * scale).toInt().coerceAtLeast(1)
        val rows = (image.height * scale).toInt().coerceAtLeast(1)
        val sample = Bitmap.createScaledBitmap(image, cols, rows, true)
        val cell = 6
        val output = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(20, 21, 30))
        val tile = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until rows) for (x in 0 until cols) {
            tile.color = sample.getPixel(x, y)
            canvas.drawCircle(x * cell + cell / 2f, y * cell + cell / 2f, cell * .43f, tile)
        }
        if (sample !== image) sample.recycle()
        return output
    }

    /** Pre-render a small painterly cover once, leaving the per-frame work to the mesh renderer. */
    private fun createOilCover(image: Bitmap): Bitmap {
        val maxSide = 300f
        val ratio = min(1f, maxSide / max(image.width, image.height))
        val w = (image.width * ratio).toInt().coerceAtLeast(1)
        val h = (image.height * ratio).toInt().coerceAtLeast(1)
        val sample = Bitmap.createScaledBitmap(image, w, h, true)
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sample, 0f, 0f, brush)
        val random = java.util.Random(2097L)
        val step = 4
        for (y in 0 until h step step) for (x in 0 until w step step) {
            val px = (x + random.nextInt(step)).coerceAtMost(w - 1)
            val py = (y + random.nextInt(step)).coerceAtMost(h - 1)
            val color = sample.getPixel(px, py)
            if (Color.alpha(color) == 0) continue
            val angle = sin(py * .028 + px * .015) * .9 + cos(px * .023 - py * .018) * .6
            val length = 5f + random.nextFloat() * 6f
            val dx = cos(angle).toFloat() * length
            val dy = sin(angle).toFloat() * length
            brush.strokeCap = Paint.Cap.ROUND
            brush.strokeWidth = 2.8f + random.nextFloat() * 2.8f
            brush.color = Color.argb(200, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawLine(px - dx * .5f, py - dy * .5f, px + dx * .5f, py + dy * .5f, brush)
            // A narrow, lighter edge gives the impression of raised paint.
            brush.strokeWidth = .7f
            brush.color = Color.argb(75, min(255, Color.red(color) + 45), min(255, Color.green(color) + 45), min(255, Color.blue(color) + 45))
            canvas.drawLine(px - dx * .4f, py - dy * .4f - 1f, px + dx * .3f, py + dy * .3f - 1f, brush)
        }
        if (sample !== image) sample.recycle()
        return output
    }
    fun setLyrics(value: List<LyricLine>) { cancelBrowsing(); lines = value.map { it.copy(words = it.words.toList()) }.sortedBy { it.startMs }; dirty = true; snap = true; invalidate() }
    fun setLrc(lrc: String) = setLyrics(LrcParser.parse(lrc))
    fun setLyricOffsetMs(offsetMs: Long) { offset = offsetMs; snap = true; invalidate() }
    @JvmOverloads fun setPlaybackState(positionMs: Long, playing: Boolean, playbackSpeed: Float = 1f) {
        require(playbackSpeed.isFinite() && playbackSpeed >= 0f)
        val now = SystemClock.elapsedRealtime()
        if (!stateSet || abs(positionMs.toDouble() - positionAt(now)) > 1000) snap = true
        if (this.playing) backgroundElapsedMs += (now - backgroundStartedMs).coerceAtLeast(0)
        backgroundStartedMs = now
        basePosition = positionMs.coerceAtLeast(0); baseClock = now; this.playing = playing; speed = playbackSpeed; stateSet = true; invalidate()
    }
    private fun positionAt(now: Long) = if (playing) basePosition + ((now - baseClock) * speed).toLong() else basePosition
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); dirty = true; snap = true }
    private fun layout(text: String, p: TextPaint, w: Int, centered: Boolean = false): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, p, w.coerceAtLeast(1)).setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(dp(5f), 1f).build()
    private fun rebuild() {
        dirty = false
        val content = if (lyricStyle == LyricStyle.PENDOLO) ((width - paddingLeft - paddingRight) * (if (width > height) .62f else .58f) - dp(24f)).toInt().coerceAtLeast(1)
        else if (lyricStyle == LyricStyle.CAPPELLA) min((width - paddingLeft - paddingRight) * .75f - dp(30f), width - paddingLeft - paddingRight - dp(40f)).toInt().coerceAtLeast(1)
        else (width - paddingLeft - paddingRight - dp(48f)).toInt().coerceAtLeast(1)
        val responsiveScale = ResponsiveTypography.scale(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom, resources.displayMetrics.density)
        // Give the cinematic styles more presence on phones, tapering back to the
        // existing tablet size. The height cap still protects short landscape views.
        val cinematic = lyricStyle == LyricStyle.TILT || lyricStyle == LyricStyle.DIORAMA
        val shortDp = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom) / resources.displayMetrics.density
        val phoneBoost = if (cinematic) 1f + .22f * ((800f - shortDp) / 200f).coerceIn(0f, 1f) else 1f
        val fontSize = min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, resources.displayMetrics) * responsiveScale * phoneBoost, content * if (cinematic) .16f else .13f)
        val heightLimit = if (width > height) .085f else if (cinematic) .14f else .12f
        textPaint.textSize = (min(fontSize, height * heightLimit) * if (lyricStyle == LyricStyle.CAPPELLA) .83f else 1f).coerceAtLeast(1f)
        textPaint.typeface = when (lyricStyle) {
            LyricStyle.PENDOLO -> Typeface.create("sans-serif", Typeface.BOLD)
            LyricStyle.TILT -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            LyricStyle.STILL -> Typeface.create("serif", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.BOLD)
        }
        smallPaint.textSize = textPaint.textSize * .52f
        val center = lyricStyle == LyricStyle.STILL || lyricStyle == LyricStyle.TILT || lyricStyle == LyricStyle.DIORAMA
        var top = 0f
        rows = lines.map { line ->
            val main = layout(line.text, textPaint, content, center)
            val sub = line.translation?.takeIf { it.isNotBlank() }?.let { layout(it, smallPaint, content, center) }
            val h = main.height + (sub?.height?.plus(dp(7f)) ?: 0f)
            val spacing = if (lyricStyle == LyricStyle.PENDOLO) min(dp(30f), height * .075f) else min(dp(32f), height * .08f)
            Row(line, main, sub, top, h, if (lyricStyle == LyricStyle.CLASSIC) prepareClassic(line) else null).also { top += h + spacing }
        }
    }
    private fun prepareClassic(line: LyricLine): ClassicPrepared {
        val tokens = LyricTokens.classicPhrases(line)
        val measurePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val responsiveScale = ResponsiveTypography.scale(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom, resources.displayMetrics.density)
            textSize = min(dp(36f) * responsiveScale, min(width * .10f, height * .085f)).coerceAtLeast(1f)
        }
        // Reserve room for the selected word's glow and scale so long words stay inside the viewport.
        val maxWidth = (width - paddingLeft - paddingRight - dp(72f)).coerceAtLeast(1f)
        val gap = dp(10f)
        val items = tokens.map { (offset, text) -> ClassicItem(offset, text, measurePaint.measureText(text), LyricTokens.timedRange(line, offset, text.length)) }
        val tokenRows = mutableListOf<MutableList<Int>>()
        var used = 0f
        items.forEachIndexed { i, token ->
            if (tokenRows.isEmpty() || (used + gap + token.width > maxWidth && tokenRows.last().isNotEmpty())) { tokenRows.add(mutableListOf()); used = 0f }
            if (tokenRows.last().isNotEmpty()) used += gap
            tokenRows.last().add(i); used += token.width
        }
        val lineHeight = measurePaint.fontSpacing * 1.32f
        val totalHeight = tokenRows.size * lineHeight
        val maxRowWidth = tokenRows.maxOfOrNull { row -> row.sumOf { items[it].width.toDouble() }.toFloat() + gap * (row.size - 1) } ?: 1f
        val scale = min(1f, min(height * .48f / totalHeight.coerceAtLeast(1f), maxWidth * .90f / maxRowWidth.coerceAtLeast(1f)))
        return ClassicPrepared(items, tokenRows, measurePaint.textSize, lineHeight, gap, scale)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (dirty) rebuild()
        val now = SystemClock.elapsedRealtime(); val time = positionAt(now) + offset
        val backgroundTime = backgroundElapsedMs + if (playing) (now - backgroundStartedMs).coerceAtLeast(0) else 0L
        if (coverBackgroundEnabled) drawBackground(canvas, backgroundTime)
        if (rows.isEmpty()) {
            drawEmptyLyricsCover(canvas, backgroundTime)
            val hasForeground = emptyLyricsCoverEnabled
            if (playing && isShown && windowVisibility == VISIBLE && (hasForeground || coverBackgroundEnabled && backgroundStyle == BackgroundStyle.LIQUID)) postInvalidateOnAnimation()
            return
        }
        var lo = 0; var hi = rows.lastIndex; var current = -1
        while (lo <= hi) { val m = (lo + hi) ushr 1; if (rows[m].line.startMs <= time) { current = m; lo = m + 1 } else hi = m - 1 }
        val elapsed = if (lastFrame == 0L) 16 else (now - lastFrame).coerceIn(0, 50); lastFrame = now
        val target = rows.getOrNull(current)?.let { it.top + if (lyricStyle == LyricStyle.PENDOLO) it.height / 2f else 0f } ?: 0f
        if (!browsing) { if (snap) { scroll = target; snap = false } else scroll += (target - scroll) * (1f - exp(-elapsed / 240f)) }
        // Clockwork is decorative and may extend behind the cutout/status bar. Lyrics
        // remain clipped to safe padding below.
        if (lyricStyle == LyricStyle.PENDOLO && current >= 0) {
            canvas.save()
            canvas.clipRect(0, 0, width, height)
            canvas.translate(0f, -height * if (width > height) .07f else .14f)
            drawPendoloClock(canvas, current, time)
            canvas.restore()
        }
        canvas.save(); canvas.clipRect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        // Keep the artwork full-screen while moving lyric compositions into the upper portion.
        canvas.translate(0f, -height * if (width > height) .07f else .14f)
        drawAtmosphere(canvas, current, time)
        when (lyricStyle) {
            LyricStyle.CLASSIC -> drawClassic(canvas, current, time)
            LyricStyle.PARTITA -> drawPartita(canvas, current, time)
            LyricStyle.CAPPELLA -> drawRail(canvas, current, time, true)
            LyricStyle.TILT -> drawTilt(canvas, current, time)
            LyricStyle.DIORAMA -> drawDiorama(canvas, current, time)
            LyricStyle.MONET -> drawRail(canvas, current, time, false)
            LyricStyle.PENDOLO -> drawPendolo(canvas, current, time)
            LyricStyle.STILL -> drawStill(canvas, current)
        }
        canvas.restore()
        if (isShown && windowVisibility == VISIBLE) {
            if (playing && speed > 0 || !browsing && abs(target - scroll) > .5f) postInvalidateOnAnimation()
            else if (playing && coverBackgroundEnabled && backgroundStyle == BackgroundStyle.LIQUID) postInvalidateDelayed(33)
        }
    }
    private fun drawAtmosphere(c: Canvas, current: Int, time: Long) {
        val row = rows.getOrNull(current) ?: return
        if (lyricStyle == LyricStyle.STILL || lyricStyle == LyricStyle.CAPPELLA) return
        val entrance = 1f - LyricMotion.enter(time - row.line.startMs, 900)
        val subtle = lyricStyle == LyricStyle.CAPPELLA || lyricStyle == LyricStyle.MONET
        val glowAlpha = if (subtle) 16 else (22 + 24 * entrance).toInt()
        val color = Color.argb(glowAlpha, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor))
        paint.shader = RadialGradient(width * .5f, height * .48f, min(width, height) * .48f, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        if (subtle || lyricStyle == LyricStyle.DIORAMA || lyricStyle == LyricStyle.PENDOLO) return
        // A small deterministic field follows playback time and freezes while paused.
        paint.style = Paint.Style.FILL
        for (i in 0 until 9) {
            val phase = time / (1800.0 + i * 97.0) + i * 2.17
            val x = width * (.08f + ((i * 137 % 83) / 100f)) + dp(8f) * sin(phase).toFloat()
            val y = height * (.19f + ((i * 53 % 60) / 100f)) + dp(10f) * cos(phase * .7).toFloat()
            val alpha = (30 + 35 * (.5 + .5 * sin(phase))).toInt()
            paint.color = Color.argb(alpha, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor))
            c.drawCircle(x, y, dp(if (i % 3 == 0) 1.7f else .8f), paint)
        }
    }
    private fun drawBackground(c: Canvas, motionTimeMs: Long) {
        c.drawColor(0xff10121b.toInt())
        val mosaic = backgroundStyle == BackgroundStyle.MOSAIC
        val posterOil = backgroundStyle == BackgroundStyle.OIL
        val background = when { mosaic -> mosaicCover; posterOil -> oilCover; else -> blurred }
        background?.takeUnless { it.isRecycled }?.let { image ->
            val scale = max(width.toFloat() / image.width, height.toFloat() / image.height) * if (posterOil || mosaic) 1.28f else 1.55f
            val w = image.width * scale; val h = image.height * scale
            val left = (width - w) / 2f; val top = (height - h) / 2f
            paint.color = Color.WHITE; paint.shader = null
            if (backgroundStyle == BackgroundStyle.LIQUID) {
                val seconds = motionTimeMs / 1000.0 * .80
                var i = 0
                for (y in 0..meshRows) for (x in 0..meshColumns) {
                    val u = x.toFloat() / meshColumns; val v = y.toFloat() / meshRows
                    val horizontal = sin(v * 5.7 + seconds * .71 + sin(seconds * .19 + u * 2.1) * 1.7) * cos(u * 3.1 - seconds * .27 + cos(v * 4.2 + seconds * .13)) + .42 * sin(u * 8.2 + v * 4.3 - seconds * .43) + .26 * cos(v * 11.4 + seconds * .31 + sin(u * 5.0 + seconds * .11))
                    val vertical = cos(u * 4.6 - seconds * .51 + cos(seconds * .17 + v * 1.8) * 1.4) * sin(v * 3.2 + seconds * .33 + sin(u * 4.1 - seconds * .14)) + .45 * cos(u * 6.7 - v * 5.2 + seconds * .53) + .22 * sin(u * 10.1 + seconds * .23 + cos(v * 3.7 - seconds * .12))
                    meshPoints[i++] = left + u * w + horizontal.toFloat() * width * .10f
                    meshPoints[i++] = top + v * h + vertical.toFloat() * height * .075f
                }
                c.drawBitmapMesh(image, meshColumns, meshRows, meshPoints, 0, null, 0, paint)
                // Broad, slowly moving translucent cover-coloured light adds visible flow even
                // where the heavily blurred image has little local texture.
                for (i in 0..1) {
                    val phase = seconds * if (i == 0) .42 else -.34
                    val x = width * (.5f + .38f * sin(phase + i * 2.0).toFloat())
                    val y = height * (.5f + .34f * cos(phase * .8 + i * 2.4).toFloat())
                    val sampled = image.getPixel(if (i == 0) 0 else image.width - 1, if (i == 0) 0 else image.height - 1)
                    val color = Color.argb(100, Color.red(sampled), Color.green(sampled), Color.blue(sampled))
                    paint.shader = RadialGradient(x, y, max(width, height) * .7f, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
                paint.shader = null
            } else {
                rect.set(left, top, left + w, top + h)
                paint.isFilterBitmap = true
                c.drawBitmap(image, null, rect, paint)
            }
        }
        paint.color = when { mosaic -> 0xC20B101A.toInt(); posterOil -> 0xA50B101A.toInt(); else -> 0x99090c17.toInt() }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
    private fun progress(index: Int, time: Long): Float {
        val line = rows[index].line
        val end = rows.getOrNull(index + 1)?.line?.startMs ?: (line.words.lastOrNull()?.endMs?.takeIf { it > line.startMs } ?: line.startMs + 4000)
        return ((time - line.startMs).toDouble() / (end - line.startMs).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    }
    private fun wordProgress(line: LyricLine, offset: Int, fallback: Float, time: Long): Float {
        var search = 0
        for (word in line.words) {
            val start = line.text.indexOf(word.text, search); if (start < 0) continue
            val end = start + word.text.length; search = end
            if (offset in start until end) return if (word.endMs <= word.startMs) if (time >= word.startMs) 1f else 0f else ((time - word.startMs).toFloat() / (word.endMs - word.startMs)).coerceIn(0f, 1f)
        }
        return fallback
    }
    /** Lighten dark user-selected colours only in the dark chat bubble. */
    private fun readableBubbleColor(color: Int): Int {
        val red = Color.red(color); val green = Color.green(color); val blue = Color.blue(color)
        val brightness = .2126f * red + .7152f * green + .0722f * blue
        if (brightness >= 190f) return Color.rgb(red, green, blue)
        val mix = ((190f - brightness) / (255f - brightness)).coerceIn(0f, 1f)
        return Color.rgb((red + (255 - red) * mix).toInt(), (green + (255 - green) * mix).toInt(), (blue + (255 - blue) * mix).toInt())
    }
    private fun drawTimed(c: Canvas, row: Row, index: Int, time: Long) {
        textPaint.clearShadowLayer()
        textPaint.color = when (lyricStyle) {
            LyricStyle.CAPPELLA -> 0xD9FFFFFF.toInt()
            LyricStyle.TILT, LyricStyle.PENDOLO -> Color.WHITE
            else -> inactiveColor
        }
        row.layout.draw(c)
        val highlight = if (lyricStyle == LyricStyle.CAPPELLA) readableBubbleColor(activeColor) else activeColor
        val line = row.line
        if (lyricStyle == LyricStyle.PENDOLO || lyricStyle == LyricStyle.TILT || lyricStyle == LyricStyle.DIORAMA) {
            if (line.words.isEmpty()) {
                if (lyricStyle == LyricStyle.DIORAMA && line.text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN }) {
                    val tokens = LyricTokens.words(line.text)
                    val position = progress(index, time) * tokens.size
                    tokens.forEachIndexed { tokenIndex, (start, text) ->
                        drawSequentialHighlight(c, row.layout, start, start + text.length, (position - tokenIndex).coerceIn(0f, 1f), highlight)
                    }
                } else drawSequentialHighlight(c, row.layout, 0, line.text.length, progress(index, time), highlight)
            } else {
                var search = 0
                for (word in line.words) {
                    if (word.text.isEmpty()) continue
                    val start = line.text.indexOf(word.text, search)
                    if (start < 0) continue
                    val end = start + word.text.length
                    search = end
                    val fraction = if (word.endMs <= word.startMs) {
                        if (time >= word.startMs) 1f else 0f
                    } else ((time - word.startMs).toDouble() / (word.endMs - word.startMs)).toFloat().coerceIn(0f, 1f)
                    drawSequentialHighlight(c, row.layout, start, end, fraction, highlight)
                }
            }
        } else if (line.words.isEmpty()) {
            val p = progress(index, time)
            c.save(); c.clipRect(0f, 0f, row.layout.width * p, row.layout.height.toFloat()); textPaint.color = highlight
            if (lyricStyle != LyricStyle.CAPPELLA) textPaint.setShadowLayer(dp(4f), 0f, 0f, activeColor)
            row.layout.draw(c); textPaint.clearShadowLayer(); c.restore()
        } else {
            var search = 0
            line.words.forEach { word ->
                val start = line.text.indexOf(word.text, search)
                if (start >= 0) {
                    val end = start + word.text.length; search = end
                    val p = if (word.endMs <= word.startMs) if (time >= word.startMs) 1f else 0f else ((time - word.startMs).toFloat() / (word.endMs - word.startMs)).coerceIn(0f, 1f)
                    if (p > 0) {
                        path.reset(); row.layout.getSelectionPath(start, end, path)
                        c.save(); c.clipPath(path)
                        if (row.layout.getLineForOffset(start) == row.layout.getLineForOffset((end - 1).coerceAtLeast(start))) {
                            val a = row.layout.getPrimaryHorizontal(start); val b = row.layout.getPrimaryHorizontal(end)
                            if (b >= a) c.clipRect(a, 0f, a + (b - a) * p, row.layout.height.toFloat()) else c.clipRect(a - (a - b) * p, 0f, a, row.layout.height.toFloat())
                        }
                        textPaint.color = highlight
                        if (lyricStyle != LyricStyle.CAPPELLA) textPaint.setShadowLayer(dp(4f), 0f, 0f, activeColor)
                        row.layout.draw(c); textPaint.clearShadowLayer(); c.restore()
                    }
                }
            }
        }
    }
    /** Clip each visual row separately so a wrapped word or line reveals in reading order. */
    private fun drawSequentialHighlight(c: Canvas, layout: Layout, start: Int, end: Int, progress: Float, color: Int) {
        if (start >= end || progress <= 0f) return
        val segments = mutableListOf<Triple<Int, Int, Int>>()
        for (line in 0 until layout.lineCount) {
            val from = max(start, layout.getLineStart(line))
            val to = min(end, layout.getLineVisibleEnd(line))
            if (from < to) segments.add(Triple(line, from, to))
        }
        val weights = segments.map { (_, from, to) -> layout.text.subSequence(from, to).toString().codePointCount(0, to - from) }
        segments.forEachIndexed { index, (line, from, to) ->
            val fraction = LineReveal.fraction(progress, weights, index)
            if (fraction <= 0f) return@forEachIndexed
            path.reset(); layout.getSelectionPath(from, to, path)
            val bounds = RectF(); path.computeBounds(bounds, true)
            c.save(); c.clipPath(path)
            if (fraction < 1f && !bounds.isEmpty) {
                val revealed = bounds.width() * fraction
                if (layout.getParagraphDirection(line) < 0) c.clipRect(bounds.right - revealed, bounds.top, bounds.right, bounds.bottom)
                else c.clipRect(bounds.left, bounds.top, bounds.left + revealed, bounds.bottom)
            }
            textPaint.color = color; textPaint.setShadowLayer(dp(4f), 0f, 0f, color)
            layout.draw(c); textPaint.clearShadowLayer(); c.restore()
        }
    }
    private fun glyphs(text: String): List<Pair<Int, String>> {
        val it = BreakIterator.getCharacterInstance(Locale.ROOT); it.setText(text); val result = mutableListOf<Pair<Int, String>>()
        var a = it.first(); var b = it.next(); while (b != BreakIterator.DONE) { result.add(a to text.substring(a, b)); a = b; b = it.next() }; return result
    }
    private fun drawClassic(c: Canvas, current: Int, time: Long) {
        val active = rows.getOrNull(current) ?: return
        val age = (time - active.line.startMs).coerceAtLeast(0)
        val interval = rows.getOrNull(current + 1)?.line?.startMs?.minus(active.line.startMs) ?: 4000L
        val transitionMs = (interval / 9).coerceIn(250L, 550L)
        if (current > 0 && age < transitionMs) drawClassicPhrases(c, current - 1, time, 1f - LyricMotion.enter(age, transitionMs), true)
        drawClassicPhrases(c, current, time, 1f, false)
    }

    private fun drawClassicPhrases(c: Canvas, index: Int, time: Long, opacity: Float, outgoing: Boolean) {
        val row = rows[index]
        val prepared = row.classic ?: return
        val tokens = prepared.items
        if (tokens.isEmpty()) return
        val savedSize = textPaint.textSize
        val savedTypeface = textPaint.typeface
        val savedAlign = textPaint.textAlign
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.textSize = prepared.fontSize
        textPaint.textAlign = Paint.Align.LEFT
        val gap = prepared.gap
        val tokenRows = prepared.rows
        val lineHeight = prepared.lineHeight
        val totalHeight = tokenRows.size * lineHeight
        val scale = prepared.scale
        val wave = LyricMotion.wave(time, 7000)
        val duration = (rows.getOrNull(index + 1)?.line?.startMs?.minus(row.line.startMs) ?: 4000L).coerceAtLeast(1L)
        val entryDuration = (duration / 9).coerceIn(250L, 550L)
        val transition = if (outgoing) 1f - opacity else 1f - LyricMotion.enter(time - row.line.startMs, entryDuration)
        // Text and timestamp seed a stable arrangement, including across seek and redraw.
        val seed = (row.line.text.hashCode().toLong() xor row.line.startMs) and 0x7fffffff
        val horizontalBias = ((seed % 5).toInt() - 2) * dp(5f)
        val verticalBias = (((seed / 7) % 5).toInt() - 2) * dp(4f)
        val startY = height * .49f - totalHeight * scale / 2f + dp(6f) * wave + verticalBias + if (outgoing) -dp(20f) * transition else dp(18f) * transition
        val overall = progress(index, time)
        c.save(); c.translate(width / 2f + horizontalBias, startY); c.scale(scale * (1f + if (outgoing) .04f * transition else -.04f * transition), scale * (1f + if (outgoing) .04f * transition else -.04f * transition))
        tokenRows.forEachIndexed { lineIndex, indices ->
            val rowWidth = indices.sumOf { tokens[it].width.toDouble() }.toFloat() + gap * (indices.size - 1)
            var x = -rowWidth / 2f
            indices.forEach { tokenIndex ->
                val token = tokens[tokenIndex]
                val w = token.width
                val fallback = (overall * tokens.size - tokenIndex).coerceIn(0f, 1f)
                val timed = token.timing
                val singing = if (timed == null) fallback else if (timed.last <= timed.first) { if (time >= timed.first) 1f else 0f } else ((time - timed.first).toDouble() / (timed.last - timed.first)).toFloat().coerceIn(0f, 1f)
                val step = (entryDuration / (tokens.size.coerceAtLeast(1) + 2)).coerceIn(25L, 55L)
                val entrance = if (outgoing) 1f else LyricMotion.enter(time - row.line.startMs - tokenIndex * step, 220)
                val alpha = (opacity * entrance).coerceIn(0f, 1f)
                if (alpha > 0f) {
                    c.save()
                    val stagger = dp((((seed + tokenIndex * 11) % 9).toInt() - 4).toFloat())
                    c.translate(x, lineIndex * lineHeight + textPaint.textSize + stagger - dp(5f) * sin(singing * PI).toFloat())
                    c.rotate((((seed / 11 + tokenIndex * 5) % 7).toInt() - 3).toFloat())
                    val pop = 1f + .10f * sin(singing * PI).toFloat(); c.scale(pop, pop)
                    val color = if (singing > .02f && singing < .98f || tokenIndex % 5 == 1) accent else Color.WHITE
                    textPaint.color = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
                    if (!outgoing && singing > .02f && singing < .98f) textPaint.setShadowLayer(dp(7f), 0f, 0f, accent)
                    c.drawText(token.text, 0f, 0f, textPaint); textPaint.clearShadowLayer(); c.restore()
                }
                x += w + gap
            }
        }
        c.restore(); textPaint.textSize = savedSize; textPaint.typeface = savedTypeface; textPaint.textAlign = savedAlign
    }
    private fun drawPartita(c: Canvas, current: Int, time: Long) {
        val row = rows.getOrNull(current) ?: return; val chars = glyphs(row.line.text); if (chars.isEmpty()) return
        // Scripts that rely on horizontal shaping (including Latin, Arabic and Indic) stay in
        // a normal shaped layout. CJK-only text retains the vertical column treatment.
        val cjkOnly = row.line.text.codePoints().toArray().all { cp ->
            val script = Character.UnicodeScript.of(cp)
            Character.isWhitespace(cp) || !Character.isLetterOrDigit(cp) ||
                script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA ||
                script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
        }
        if (!cjkOnly) {
            // Keep each word shaped by Android's text engine, while stacking words vertically.
            val tokens = LyricTokens.words(row.line.text)
            if (tokens.isEmpty()) return
            val availableWidth = (width - dp(48f)).toInt().coerceAtLeast(1)
            val layouts = tokens.map { layout(it.second, textPaint, availableWidth, true) }
            val spacing = dp(8f)
            val stackHeight = layouts.sumOf { it.height.toDouble() }.toFloat() + spacing * (layouts.size - 1)
            // Fit a long sentence on screen, preserving shaped word wrapping.
            val scale = min(1f, (height - dp(48f)).coerceAtLeast(1f) / stackHeight.coerceAtLeast(1f))
            val originY = (height - stackHeight * scale) / 2f
            val total = progress(current, time)
            c.save(); c.translate((width - availableWidth * scale) / 2f, originY); c.scale(scale, scale)
            var y = 0f
            tokens.forEachIndexed { i, token ->
                val fallback = (total * tokens.size - i).coerceIn(0f, 1f)
                val fraction = wordProgress(row.line, token.first, fallback, time)
                val reveal = if (row.line.words.isEmpty()) (total * tokens.size - i + 1f).coerceIn(0f, 1f) else if (fraction > 0f) 1f else 0f
                if (reveal > 0f) {
                    c.save(); c.translate(0f, y)
                    textPaint.color = if (i % 4 == 2) accent else Color.WHITE
                    layouts[i].draw(c); c.restore()
                }
                y += layouts[i].height + spacing
            }
            val last = tokens.last()
            val lastFraction = wordProgress(row.line, last.first, (total * tokens.size - tokens.lastIndex).coerceIn(0f, 1f), time)
            paint.color = accent; paint.strokeWidth = dp(1f)
            c.drawLine(availableWidth * .35f, y + dp(4f), availableWidth * (.35f + .3f * lastFraction), y + dp(4f), paint)
            c.restore(); return
        }
        val savedSize = textPaint.textSize; val savedTypeface = textPaint.typeface
        val responsiveScale = ResponsiveTypography.scale(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom, resources.displayMetrics.density)
        val desiredCell = dp(46f) * responsiveScale
        val availableHeight = (height * .66f).coerceAtLeast(1f)
        val perColumn = min(5, max(1, (availableHeight / desiredCell).toInt())); val cols = ceil(chars.size.toDouble() / perColumn).toInt()
        val cell = min(desiredCell, min((width - paddingLeft - paddingRight - dp(48f)) / cols.coerceAtLeast(1), availableHeight / min(chars.size, perColumn).coerceAtLeast(1))).coerceAtLeast(1f)
        val size = cell * .72f
        textPaint.textSize = size; textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD); textPaint.textAlign = Paint.Align.CENTER
        val originY = (height - min(chars.size, perColumn) * cell) / 2f + LyricMotion.wave(time, 7000) * dp(5f)
        val total = progress(current, time)
        chars.forEachIndexed { i, (offset, value) ->
            val col = i / perColumn; val line = i % perColumn; val x = width / 2f + (cols - 1) * cell / 2f - col * cell; val y = originY + (line + .8f) * cell
            val p = wordProgress(row.line, offset, (total * chars.size - i).coerceIn(0f, 1f), time)
            val reveal = (total * chars.size - i + 1f).coerceIn(0f, 1f); if (reveal <= 0) return@forEachIndexed
            c.save(); c.translate(x, y); val scale = .85f + .15f * reveal + .08f * sin(p * PI).toFloat(); c.scale(scale, scale)
            textPaint.color = if (i % 4 == 2) accent else Color.WHITE; c.drawText(value, 0f, 0f, textPaint); c.restore()
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = accent
        for (col in 0 until cols) {
            val x = width / 2f + (cols - 1) * cell / 2f - col * cell
            val count = min(perColumn, chars.size - col * perColumn)
            val lastIndex = col * perColumn + count - 1
            val lastOffset = chars[lastIndex].first
            val lastProgress = wordProgress(row.line, lastOffset, (total * chars.size - lastIndex).coerceIn(0f, 1f), time)
            val fallbackAppearance = (total * chars.size - lastIndex).coerceIn(0f, 1f)
            val lineReveal = max(lastProgress, fallbackAppearance.takeIf { row.line.words.isEmpty() } ?: 0f)
            val y = originY + count * cell + dp(6f)
            c.drawLine(x - cell * .3f, y, x - cell * .3f + cell * .6f * lineReveal, y, paint)
            if (lineReveal > .8f) c.drawLine(x + cell * .3f, y, x + cell * .3f, y - dp(9f) * ((lineReveal - .8f) / .2f), paint)
        }
        paint.style = Paint.Style.FILL; textPaint.textAlign = Paint.Align.LEFT; textPaint.textSize = savedSize; textPaint.typeface = savedTypeface
    }
    private fun drawRail(c: Canvas, current: Int, time: Long, bubbles: Boolean) {
        val x = dp(if (bubbles) 30f else 24f)
        // Counter the shared lyric translation and centre the active bubble; leave
        // space for the demo's bottom controls on short screens.
        val upperShift = height * if (width > height) .07f else .14f
        val activeHeight = rows.getOrNull(current)?.height ?: 0f
        val targetTop = min(height * .50f - activeHeight / 2f, height - dp(72f) - activeHeight)
        val anchor = if (bubbles) targetTop.coerceAtLeast(dp(20f)) + upperShift else height * .45f
        rows.forEachIndexed { i, row ->
            if (bubbles && (current < 0 || i > current)) return@forEachIndexed
            val y = anchor + row.top - scroll; if (y + row.height < -dp(30f) || y > height + dp(30f)) return@forEachIndexed
            val maxLineWidth = (0 until row.layout.lineCount).maxOfOrNull { row.layout.getLineWidth(it) } ?: 0f
            val subWidth = row.translation?.let { sub -> (0 until sub.lineCount).maxOfOrNull { sub.getLineWidth(it) } } ?: 0f
            val contentWidth = max(maxLineWidth, subWidth).coerceAtMost(row.layout.width.toFloat())
            val originX = if (!bubbles) x else if (i % 2 == 0) dp(35f) else width - dp(35f) - contentWidth
            c.save()
            val enter = if (bubbles && i == current) LyricMotion.enter(time - row.line.startMs, 400) else 1f
            c.translate(originX, y + dp(12f) * (1f - enter))
            if (bubbles && i == current) {
                val pivot = if (i % 2 == 0) 0f else contentWidth
                val scale = .96f + .04f * enter
                c.scale(scale, scale, pivot, row.height)
                c.saveLayerAlpha(-dp(18f), -dp(14f), contentWidth + dp(18f), row.height + dp(14f), (enter * 255).toInt().coerceIn(0, 255))
            }
            if (bubbles) {
                // Dark translucent surfaces keep text legible on both bright and dark artwork.
                paint.color = if (i % 2 == 0) 0xD91A2131.toInt() else 0xD9322444.toInt()
                rect.set(-dp(15f), -dp(10f), contentWidth + dp(15f), row.height + dp(10f))
                c.drawRoundRect(rect, dp(18f), dp(18f), paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f); paint.color = 0x40FFFFFF
                c.drawRoundRect(rect, dp(18f), dp(18f), paint); paint.style = Paint.Style.FILL
            }
            if (i == current) drawTimed(c, row, i, time) else { textPaint.color = Color.argb(if (bubbles) 195 else if (browsing && abs(row.top - scroll) < row.height + dp(32f)) 155 else if (abs(i - current) <= 1) 95 else 45, 255, 255, 255); row.layout.draw(c) }
            row.translation?.let { c.save(); c.translate(0f, row.layout.height + dp(7f)); smallPaint.color = if (bubbles) 0xC9FFFFFF.toInt() else translationColor; it.draw(c); c.restore() }
            if (bubbles && i == current) c.restore()
            c.restore()
        }
    }
    private fun drawTilt(c: Canvas, current: Int, time: Long) {
        val row = rows.getOrNull(current) ?: return
        val entry = LyricMotion.enter(time - row.line.startMs); val wave = LyricMotion.wave(time, 7000)
        val center = height / 2f + dp(4f) * wave
        for (delta in -1..1) {
            val item = rows.getOrNull(current + delta) ?: continue
            val scale = if (delta == 0) 1f else .73f
            val y = when (delta) { -1 -> center - row.layout.height / 2f - dp(48f) - item.layout.height * scale; 1 -> center + row.layout.height / 2f + dp(48f); else -> center - row.layout.height / 2f + dp(16f) * (1 - entry) }
            c.save(); c.translate((width - item.layout.width * scale) / 2f, y); c.scale(scale, scale)
            val angle = if (delta == 0) -2f else floatArrayOf(-5f, 3f, -3f, 5f)[(current + delta).coerceAtLeast(0) % 4]
            c.rotate(angle, item.layout.width / 2f, item.layout.height / 2f)
            if (delta == 0) drawTimed(c, item, current, time) else {
                val original = textPaint.typeface; textPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                textPaint.color = Color.argb(85, 255, 255, 255); item.layout.draw(c); textPaint.typeface = original
            }
            c.restore()
        }
    }
    private fun drawDiorama(c: Canvas, current: Int, time: Long) {
        if (current < 0) return
        // Hold the current line in place throughout its interval. Transition starts only at
        // the next timestamp; the incoming and outgoing positions are continuous there.
        val transition = if (current == 0) 1f else LyricMotion.enter(time - rows[current].line.startMs, 420)
        val camera = current - 1f + transition
        val sway = LyricMotion.wave(time, 7200)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f)
        for (i in 0 until 9) {
            val z = ((i * .117f + camera * .21f) % 1f)
            val x = width * (.07f + (i * 37 % 87) / 100f) + sway * dp(8f)
            val y = height * (.10f + z * .65f)
            val size = dp(3f) + z * dp(10f)
            paint.color = Color.argb((25 + 48 * z).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            c.save(); c.rotate(i * 29f + camera * 14f, x, y)
            when (i % 3) {
                0 -> c.drawRect(x - size, y - size, x + size, y + size, paint)
                1 -> {
                    path.reset(); path.moveTo(x, y - size); path.lineTo(x + size, y + size); path.lineTo(x - size, y + size); path.close(); c.drawPath(path, paint)
                }
                else -> {
                    val pulse = .7f + .3f * sin(time / 450.0 + i).toFloat()
                    c.drawLine(x - size * pulse, y, x + size * pulse, y, paint)
                    c.drawLine(x, y - size * pulse, x, y + size * pulse, paint)
                    c.drawCircle(x, y, dp(1.2f), paint)
                }
            }
            c.restore()
        }
        paint.style = Paint.Style.FILL
        for (i in min(rows.lastIndex, current + 2) downTo current) {
            val row = rows[i]; val depth = i - camera
            if (depth < -1.1f || depth > 2.1f) continue
            val scale = (1f - depth * .32f).coerceIn(.30f, 1.30f)
            // Stable scene positions differ in both axes, producing a change of camera direction.
            val directionX = floatArrayOf(-.24f, .20f, -.09f, .26f, 0f, -.17f)[i % 6]
            val directionY = floatArrayOf(-.08f, -.19f, .15f, -.13f, .20f, .06f)[i % 6]
            val approach = depth.coerceAtLeast(0f).coerceAtMost(1f)
            val x = (width - row.layout.width * scale) / 2f + width * directionX * approach + sway * dp(5f) * approach
            val y = height * (.52f + directionY * approach) - row.layout.height * scale / 2f + sway * dp(3f) * approach
            val alpha = (1f - abs(depth) * .58f).coerceIn(0f, 1f)
            val tilt = floatArrayOf(-5f, 4f, 2f, -3f, 6f, -2f)[i % 6] * approach
            c.save(); c.translate(x, y); c.rotate(tilt, row.layout.width * scale / 2f, row.layout.height * scale / 2f); c.scale(scale, scale)
            if (i == current) drawTimed(c, row, i, time) else { textPaint.color = Color.argb((alpha * 200).toInt(), 255, 255, 255); row.layout.draw(c) }
            c.restore()
        }
    }
    private fun drawStill(c: Canvas, current: Int) {
        if (current < 0) return
        val centerY = height * .5f
        rows.forEachIndexed { i, row ->
            val distance = row.top - scroll
            if (abs(distance) > height * .7f + row.height) return@forEachIndexed
            val emphasis = (1f - abs(distance) / (row.height + dp(32f))).coerceIn(0f, 1f)
            val scale = .66f + .34f * emphasis
            val y = centerY + distance - row.height * scale / 2f
            c.save(); c.translate((width - row.layout.width * scale) / 2f, y); c.scale(scale, scale)
            textPaint.color = if (i == current) activeColor else Color.argb((90 + 65 * emphasis).toInt(), 255, 255, 255)
            row.layout.draw(c)
            if (i == current) row.translation?.let {
                c.save(); c.translate(0f, row.layout.height + dp(7f)); smallPaint.color = translationColor; it.draw(c); c.restore()
            }
            c.restore()
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1f)
        paint.color = Color.argb(90, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor))
        c.drawLine(width * .45f, centerY + dp(32f), width * .55f, centerY + dp(32f), paint)
        paint.style = Paint.Style.FILL
    }
    private fun drawPendolo(c: Canvas, current: Int, time: Long) {
        if (current < 0) return
        val landscape = width > height
        val radius = min(width * if (landscape) .30f else .57f, height * if (landscape) .76f else .60f)
        val cx = -radius * if (landscape) .30f else .38f
        val cy = height * .50f
        for ((i, row) in rows.withIndex()) {
            val relativeY = row.top + row.height / 2f - scroll
            if (abs(relativeY) > height * .8f + row.height) continue
            val delta = i - current
            val angular = (relativeY / (radius + dp(20f))).coerceIn(-.8f, .8f)
            val curveX = cx + cos(angular) * (radius + dp(14f))
            val x = curveX.coerceIn(dp(12f), (width - row.layout.width - dp(16f)).coerceAtLeast(dp(12f)))
            val y = cy + relativeY - row.layout.height / 2f
            c.save(); c.translate(x, y); c.rotate(angular * 5f, 0f, row.layout.height / 2f)
            if (delta == 0) {
                drawTimed(c, row, i, time)
            } else {
                val original = textPaint.typeface
                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textPaint.color = Color.argb(if (abs(delta) == 1) 110 else 45, 255, 255, 255)
                row.layout.draw(c); textPaint.typeface = original
            }
            c.restore()
        }
    }
    private fun drawPendoloClock(c: Canvas, current: Int, time: Long) {
        val landscape = width > height
        val radius = min(width * if (landscape) .30f else .57f, height * if (landscape) .76f else .60f)
        val cx = -radius * if (landscape) .30f else .38f
        val cy = height * .50f
        val tick = LyricMotion.clockTick(time - rows[current].line.startMs)
        c.save(); c.rotate(tick * 1.8f, cx, cy)
        drawClockwork(c, cx, cy, radius, time)
        c.restore()
    }
    /** Native, transparent clockwork inspired by the reference's brass wheels and dark bridge. */
    private fun drawClockwork(c: Canvas, cx: Float, cy: Float, radius: Float, time: Long) {
        c.save(); c.translate(cx, cy); c.scale(radius / 200f, radius / 200f)
        val brass = 0xD7C6AF7C.toInt()
        val outline = 0xC9303438.toInt()
        paint.shader = null; paint.style = Paint.Style.STROKE
        paint.color = 0xA6E2D6B8.toInt(); paint.strokeWidth = 2f; c.drawCircle(0f, 0f, 192f, paint)
        paint.color = brass; paint.strokeWidth = 2f; c.drawCircle(0f, 0f, 184f, paint)
        paint.color = 0x70F0E3C5; paint.strokeWidth = .7f; c.drawCircle(0f, 0f, 181f, paint)
        paint.color = 0x807D8080.toInt(); paint.strokeWidth = .8f; c.drawCircle(0f, 0f, 174f, paint)
        for (i in 0 until 48) {
            val a = 2 * PI * i / 48
            val inner = if (i % 4 == 0) 166f else 171f
            paint.color = if (i % 4 == 0) brass else 0x788F9695
            c.drawLine(cos(a).toFloat() * inner, sin(a).toFloat() * inner, cos(a).toFloat() * 178f, sin(a).toFloat() * 178f, paint)
        }
        // Lower bridge: an open arc leaves the rotating gearwork visible.
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 28f; paint.color = 0xB52C3035.toInt()
        c.drawArc(-156f, -152f, 156f, 156f, 15f, 132f, false, paint)
        paint.strokeWidth = 1.2f; paint.color = 0x8FC6AF7C.toInt()
        c.drawArc(-144f, -140f, 144f, 144f, 20f, 120f, false, paint)
        val turn = (time % 60000L) / 60000f * 360f
        drawGear(c, -67f, 58f, 62f, 22, turn * .55f, brass, outline)
        drawGear(c, 73f, -67f, 47f, 18, -turn * .72f, brass, outline)
        drawGear(c, -6f, -17f, 32f, 16, turn * 1.05f, brass, outline)
        drawGear(c, 28f, 43f, 28f, 14, -turn * 1.2f, brass, outline)
        drawGear(c, 61f, 1f, 18f, 12, turn * 1.8f, brass, outline)
        // Upper balance wheel and visible inner spring.
        c.save(); c.translate(-80f, -75f); c.rotate(sin(time / 650.0).toFloat() * 15f)
        paint.style = Paint.Style.STROKE; paint.color = brass; paint.strokeWidth = 3f; c.drawCircle(0f, 0f, 37f, paint)
        paint.strokeWidth = 1f; c.drawCircle(0f, 0f, 28f, paint); c.drawCircle(0f, 0f, 16f, paint)
        c.drawLine(-35f, 0f, 35f, 0f, paint); c.drawLine(0f, -35f, 0f, 35f, paint)
        paint.style = Paint.Style.FILL; paint.color = brass; c.drawCircle(0f, 0f, 5f, paint); c.restore()
        // Fine links and screw heads echo the reference's mechanical plates.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 9f; paint.strokeCap = Paint.Cap.ROUND; paint.color = 0xC7D7CEB7.toInt()
        c.drawLine(29f, 42f, 88f, 83f, paint); c.drawLine(73f, -67f, 20f, -115f, paint)
        paint.strokeWidth = 1f; paint.color = outline
        c.drawLine(29f, 42f, 88f, 83f, paint); c.drawLine(73f, -67f, 20f, -115f, paint)
        for ((x, y) in arrayOf(-160f to 0f, 160f to 0f, 0f to -160f, 0f to 160f, 88f to 83f, 20f to -115f)) {
            paint.style = Paint.Style.FILL; paint.color = 0xDDD7CEB7.toInt(); c.drawCircle(x, y, 4f, paint)
            paint.style = Paint.Style.STROKE; paint.color = outline; paint.strokeWidth = 1f; c.drawCircle(x, y, 4f, paint); c.drawLine(x - 2f, y + 2f, x + 2f, y - 2f, paint)
        }
        paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT
        c.restore()
    }
    private fun drawGear(c: Canvas, x: Float, y: Float, r: Float, teeth: Int, rotation: Float, brass: Int, outline: Int) {
        c.save(); c.translate(x, y); c.rotate(rotation)
        val gear = Path()
        for (i in 0 until teeth * 4) {
            val a = 2 * PI * i / (teeth * 4)
            val size = if (i % 4 == 1 || i % 4 == 2) r else r * .88f
            val px = cos(a).toFloat() * size; val py = sin(a).toFloat() * size
            if (i == 0) gear.moveTo(px, py) else gear.lineTo(px, py)
        }
        gear.close()
        paint.style = Paint.Style.FILL; paint.color = brass; c.drawPath(gear, paint)
        paint.style = Paint.Style.STROKE; paint.color = outline; paint.strokeWidth = 1.5f; c.drawPath(gear, paint)
        // Dark cut-out is represented by a transparent-looking inner disc and spokes.
        paint.color = 0xD02D3238.toInt(); paint.strokeWidth = r * .14f; c.drawCircle(0f, 0f, r * .67f, paint)
        paint.color = 0x8DE9DAB9.toInt(); paint.strokeWidth = .9f; c.drawCircle(0f, 0f, r * .79f, paint)
        paint.color = brass; paint.strokeWidth = r * .11f
        for (i in 0 until 6) {
            val a = 2 * PI * i / 6
            c.drawLine(0f, 0f, cos(a).toFloat() * r * .70f, sin(a).toFloat() * r * .70f, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = brass; c.drawCircle(0f, 0f, r * .18f, paint)
        paint.style = Paint.Style.STROKE; paint.color = outline; paint.strokeWidth = 1f; c.drawCircle(0f, 0f, r * .18f, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xD18D6672.toInt(); c.drawCircle(0f, 0f, r * .07f, paint)
        c.restore()
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); if (visibility == VISIBLE) { lastFrame = 0; invalidate() } }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) { lastFrame = 0; invalidate() } }
}
