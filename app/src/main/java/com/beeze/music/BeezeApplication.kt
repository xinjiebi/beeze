package com.beeze.music

import android.app.Application
import com.beeze.music.ads.AdsManager

class BeezeApplication : Application() {
    val adsManager: AdsManager by lazy { AdsManager(this) }
    var appOpenHandledForProcess = false
}
