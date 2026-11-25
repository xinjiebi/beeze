package com.beeze.music.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.BeezeApplication
import com.beeze.music.R
import com.beeze.music.ui.theme.BeezeColors
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.Random

internal fun adAfterIndex(itemCount: Int, preferredIndex: Int): Int =
    if (itemCount > preferredIndex + 1) preferredIndex else (itemCount - 1).coerceAtLeast(0)

internal fun adInsertionIndices(itemCount: Int, preferredIndex: Int, seed: Int): Set<Int> {
    if (itemCount == 0) return emptySet()
    val indices = linkedSetOf(adAfterIndex(itemCount, preferredIndex))
    val random = Random(seed.toLong())
    var blockStart = 50
    while (blockStart < itemCount) {
        val blockEnd = (blockStart + 49).coerceAtMost(itemCount - 1)
        indices += random.nextInt(blockEnd - blockStart + 1) + blockStart
        blockStart += 50
    }
    return indices
}

@Composable
fun ListNativeAd(adKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adsManager = (context.applicationContext as BeezeApplication).adsManager
    val adsReady by adsManager.adsReady.collectAsStateWithLifecycle()
    if (!adsReady) return

    val record = remember(adKey) { adsManager.getNativeAd(adKey) }
    if (record.failed) return
    val widthDp = (LocalConfiguration.current.screenWidthDp - 40).coerceAtLeast(1)
    val heightDp = widthDp / 4
    val surfaceColor = BeezeColors.Surface.toArgb()
    val bodyColor = BeezeColors.Body.toArgb()
    val mutedColor = BeezeColors.Muted.toArgb()
    val primaryColor = BeezeColors.Primary.toArgb()

    Box(
        (if (record.loaded) modifier else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(if (record.loaded) heightDp.dp else 0.dp),
    ) {
        record.ad?.let { ad ->
            AndroidView(
                factory = { viewContext ->
                    createNativeAdView(
                        context = viewContext,
                        ad = ad,
                        heightDp = heightDp,
                        surfaceColor = surfaceColor,
                        bodyColor = bodyColor,
                        mutedColor = mutedColor,
                        primaryColor = primaryColor,
                    )
                },
            )
        }
    }
}

private fun createNativeAdView(
    context: Context,
    ad: NativeAd,
    heightDp: Int,
    surfaceColor: Int,
    bodyColor: Int,
    mutedColor: Int,
    primaryColor: Int,
): NativeAdView {
    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    val root = NativeAdView(context).apply {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
        background = GradientDrawable().apply {
            setColor(surfaceColor)
            cornerRadius = dp(12).toFloat()
        }
        clipToOutline = true
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL,
        ).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
            topMargin = dp(12)
            bottomMargin = dp(6)
        }
    }
    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        ad.icon?.drawable?.let(::setImageDrawable)
        visibility = if (ad.icon?.drawable == null) View.GONE else View.VISIBLE
    }
    content.addView(icon)

    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
            marginEnd = dp(8)
        }
    }
    val headline = TextView(context).apply {
        text = ad.headline
        textSize = 14f
        setTextColor(bodyColor)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    textColumn.addView(headline)
    val advertiser = TextView(context).apply {
        text = ad.advertiser ?: ad.store.orEmpty()
        textSize = 11f
        setTextColor(mutedColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }
    textColumn.addView(advertiser)
    content.addView(textColumn)

    val callToAction = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(96), dp(36))
        text = ad.callToAction
        textSize = 12f
        isAllCaps = false
        minWidth = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
        backgroundTintList = ColorStateList.valueOf(primaryColor)
        setTextColor(surfaceColor)
        visibility = if (ad.callToAction.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
    content.addView(callToAction)
    root.addView(content)

    val label = TextView(context).apply {
        text = context.getString(R.string.advertisement)
        textSize = 10f
        setTextColor(mutedColor)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ).apply {
            marginStart = dp(10)
            topMargin = dp(4)
        }
    }
    root.addView(label)
    val adChoices = AdChoicesView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        )
    }
    root.addView(adChoices)

    root.headlineView = headline
    root.advertiserView = advertiser
    root.iconView = icon
    root.callToActionView = callToAction
    root.adChoicesView = adChoices
    root.setNativeAd(ad)
    return root
}
