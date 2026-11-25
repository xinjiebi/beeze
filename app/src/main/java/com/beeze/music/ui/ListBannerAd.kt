package com.beeze.music.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beeze.music.BeezeApplication

@Composable
fun ListBannerAd(adKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adsManager = (context.applicationContext as BeezeApplication).adsManager
    val adsReady by adsManager.adsReady.collectAsStateWithLifecycle()
    if (!adsReady) return

    val widthDp = (LocalConfiguration.current.screenWidthDp - 40).coerceAtLeast(1)
    val record = remember(adKey, widthDp) { adsManager.getBannerAd(adKey, widthDp) }
    if (record.failed) return

    Box(
        (if (record.loaded) modifier else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        AndroidView(
            factory = {
                (record.view.parent as? ViewGroup)?.removeView(record.view)
                record.view
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (record.loaded) Modifier.wrapContentHeight() else Modifier.height(0.dp)),
        )
    }

}
