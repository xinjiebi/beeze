package com.beeze.music.ads

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beeze.music.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BannerAdRecord(val view: AdView) {
    var loaded by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var loading = false
    var attempts = 0
}

class NativeAdRecord {
    var ad: NativeAd? = null
    var loaded by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var loading = false
    var attempts = 0
}

class AdsManager(private val application: Application) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val consentInformation = UserMessagingPlatform.getConsentInformation(application)
    private val preferences = application.getSharedPreferences("ads", 0)
    private val initializeCallbacks = mutableListOf<() -> Unit>()
    private var initializing = false

    private val bannerAds = mutableMapOf<String, BannerAdRecord>()
    private val nativeAds = mutableMapOf<String, NativeAdRecord>()
    private val pendingNativeAds = ArrayDeque<String>()
    private var nativeAdsInFlight = 0
    private var nextNativeRequestAt = 0L
    private var nativeQueueScheduled = false
    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false
    private val appOpenWaiters = mutableListOf<(AppOpenAd?) -> Unit>()

    private val _adsReady = MutableStateFlow(false)
    val adsReady: StateFlow<Boolean> = _adsReady

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    suspend fun prepareAds(activity: Activity): Boolean = suspendCancellableCoroutine { continuation ->
        fun complete() {
            updatePrivacyOptions()
            if (consentInformation.canRequestAds()) {
                initializeMobileAds {
                    if (continuation.isActive) continuation.resume(true)
                }
            } else if (continuation.isActive) {
                continuation.resume(false)
            }
        }
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    complete()
                }
            },
            { complete() },
        )
        if (consentInformation.canRequestAds()) {
            initializeMobileAds {
                if (continuation.isActive) continuation.resume(true)
            }
        }
    }

    private fun initializeMobileAds(onComplete: () -> Unit) {
        if (_adsReady.value) {
            mainHandler.post(onComplete)
            return
        }
        synchronized(initializeCallbacks) {
            initializeCallbacks += onComplete
            if (initializing) return
            initializing = true
        }
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(application) {
                mainHandler.post {
                    _adsReady.value = true
                    val callbacks = synchronized(initializeCallbacks) {
                        initializing = false
                        initializeCallbacks.toList().also { initializeCallbacks.clear() }
                    }
                    callbacks.forEach { it() }
                }
            }
        }
    }

    private fun updatePrivacyOptions() {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            updatePrivacyOptions()
            if (consentInformation.canRequestAds()) initializeMobileAds {}
        }
    }

    fun getBannerAd(key: String, widthDp: Int): BannerAdRecord {
        bannerAds[key]?.let { record ->
            if (!record.loaded && !record.loading && record.attempts < MAX_AD_ATTEMPTS) {
                loadBannerAd(key, record)
            }
            return record
        }
        val record = BannerAdRecord(AdView(application))
        record.view.apply {
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(application, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    record.loading = false
                    record.loaded = true
                    Log.d(TAG, "Banner loaded: $key")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    record.loading = false
                    record.failed = true
                    Log.d(TAG, "Banner failed: $key, code=${error.code}, attempt=${record.attempts}")
                    if (record.attempts < MAX_AD_ATTEMPTS) {
                        mainHandler.postDelayed({
                            if (bannerAds[key] === record && !record.loaded) loadBannerAd(key, record)
                        }, AD_RETRY_DELAY_MS * record.attempts)
                    }
                }
            }
        }
        bannerAds[key] = record
        loadBannerAd(key, record)
        return record
    }

    private fun loadBannerAd(key: String, record: BannerAdRecord) {
        record.loading = true
        record.failed = false
        record.attempts += 1
        Log.d(TAG, "Banner requested: $key, attempt=${record.attempts}")
        record.view.loadAd(AdRequest.Builder().build())
    }

    fun getNativeAd(key: String): NativeAdRecord = obtainNativeAd(key, prioritize = true)

    private fun obtainNativeAd(key: String, prioritize: Boolean): NativeAdRecord {
        val record = nativeAds.getOrPut(key) { NativeAdRecord() }
        if (record.attempts < MAX_AD_ATTEMPTS) enqueueNativeAd(key, record, prioritize)
        return record
    }

    private fun enqueueNativeAd(key: String, record: NativeAdRecord, prioritize: Boolean) {
        if (record.loaded || record.loading) return
        if (prioritize) {
            pendingNativeAds.remove(key)
            pendingNativeAds.addFirst(key)
        } else if (!pendingNativeAds.contains(key)) {
            pendingNativeAds.addLast(key)
        }
        drainNativeQueue()
    }

    private fun drainNativeQueue() {
        if (nativeAdsInFlight >= MAX_CONCURRENT_NATIVE_ADS || pendingNativeAds.isEmpty()) return
        val delay = nextNativeRequestAt - android.os.SystemClock.uptimeMillis()
        if (delay > 0) {
            if (!nativeQueueScheduled) {
                nativeQueueScheduled = true
                mainHandler.postDelayed({
                    nativeQueueScheduled = false
                    drainNativeQueue()
                }, delay)
            }
            return
        }
        while (nativeAdsInFlight < MAX_CONCURRENT_NATIVE_ADS && pendingNativeAds.isNotEmpty()) {
            val key = pendingNativeAds.removeFirst()
            val record = nativeAds[key] ?: continue
            if (record.loaded || record.loading) continue
            loadNativeAd(key, record)
        }
    }

    private fun loadNativeAd(key: String, record: NativeAdRecord) {
        record.loading = true
        record.failed = false
        record.attempts += 1
        nativeAdsInFlight += 1
        Log.d(TAG, "Native requested: $key, attempt=${record.attempts}")
        AdLoader.Builder(application, BuildConfig.ADMOB_NATIVE_UNIT_ID)
            .forNativeAd { ad ->
                nativeAdsInFlight -= 1
                record.loading = false
                if (nativeAds[key] !== record) {
                    ad.destroy()
                    Log.d(TAG, "Native discarded: $key")
                } else {
                    record.ad = ad
                    record.loaded = true
                    Log.d(TAG, "Native loaded: $key")
                }
                drainNativeQueue()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAdsInFlight -= 1
                    record.loading = false
                    nextNativeRequestAt = android.os.SystemClock.uptimeMillis() + NATIVE_FAILURE_SPACING_MS
                    Log.d(TAG, "Native failed: $key, code=${error.code}, attempt=${record.attempts}")
                    if (nativeAds[key] === record) {
                        record.failed = true
                        if (record.attempts < MAX_AD_ATTEMPTS) {
                            val retryDelay = AD_RETRY_DELAY_MS * record.attempts
                            Log.d(TAG, "Native retry scheduled: $key, delay=${retryDelay}ms")
                            mainHandler.postDelayed({
                                if (nativeAds[key] === record && !record.loaded) {
                                    enqueueNativeAd(key, record, prioritize = false)
                                }
                            }, retryDelay)
                        }
                    }
                    drainNativeQueue()
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setReturnUrlsForImageAssets(false)
                    .build(),
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun preloadStartupAds(nativeKeys: Set<String>, widthDp: Int) {
        if (!_adsReady.value) return
        getBannerAd("home_folders", widthDp)
        nativeKeys.forEach { obtainNativeAd(it, prioritize = false) }
        Log.d(TAG, "Startup preload started: ${nativeKeys.size} native ads")
    }

    suspend fun awaitStartupAds(nativeKeys: Set<String>) {
        while (true) {
            val nativesDone = nativeKeys.all { key ->
                nativeAds[key]?.let { record ->
                    record.loaded ||
                        (record.failed && !record.loading && record.attempts >= MAX_AD_ATTEMPTS)
                } == true
            }
            val bannerDone = bannerAds["home_folders"]?.let { it.loaded || it.failed } == true
            if (nativesDone && bannerDone) return
            kotlinx.coroutines.delay(50)
        }
    }

    fun completeStartupPreload() {
        Log.d(TAG, "Startup wait finished; remaining ads continue in background")
    }

    fun preloadHomeAds(widthDp: Int) {
        if (!_adsReady.value) return
        getBannerAd("home_folders", widthDp)
        obtainNativeAd("home_for_you", prioritize = false)
    }

    fun preloadNativeAds(prefix: String, indices: Set<Int>) {
        if (!_adsReady.value) return
        indices.forEachIndexed { index, adIndex ->
            obtainNativeAd("${prefix}_$adIndex", prioritize = index == 0)
        }
    }

    fun canOfferAppOpen(): Boolean {
        val sinceLastShow = System.currentTimeMillis() - preferences.getLong(KEY_LAST_APP_OPEN_SHOWN, 0L)
        return sinceLastShow !in 0 until APP_OPEN_COOLDOWN_MS
    }

    private fun cachedAppOpenAd(): AppOpenAd? {
        val ad = appOpenAd ?: return null
        val valid = canOfferAppOpen() &&
            System.currentTimeMillis() - appOpenLoadedAt in 0 until APP_OPEN_AD_VALID_MS
        if (!valid) appOpenAd = null
        return if (valid) ad else null
    }

    fun preloadAppOpenAd() {
        if (!_adsReady.value || !canOfferAppOpen() || appOpenLoading || cachedAppOpenAd() != null) return
        CoroutineScope(Dispatchers.Main).launch { loadAppOpenAd() }
    }

    suspend fun loadAppOpenAd(): AppOpenAd? = suspendCancellableCoroutine { continuation ->
        cachedAppOpenAd()?.let {
            continuation.resume(it)
            return@suspendCancellableCoroutine
        }
        if (!_adsReady.value || !canOfferAppOpen()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        appOpenWaiters += { ad ->
            if (continuation.isActive) continuation.resume(ad)
        }
        if (appOpenLoading) return@suspendCancellableCoroutine
        appOpenLoading = true
        Log.d(TAG, "App open requested")
        AppOpenAd.load(
            application,
            BuildConfig.ADMOB_APP_OPEN_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    finishAppOpenLoad(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "App open failed: code=${error.code}, ${error.message}")
                    finishAppOpenLoad(null)
                }
            },
        )
    }

    private fun finishAppOpenLoad(ad: AppOpenAd?) {
        mainHandler.post {
            appOpenLoading = false
            if (ad != null) {
                appOpenAd = ad
                appOpenLoadedAt = System.currentTimeMillis()
                Log.d(TAG, "App open loaded")
            }
            val waiters = appOpenWaiters.toList()
            appOpenWaiters.clear()
            waiters.forEach { it(ad) }
        }
    }

    suspend fun showAppOpenAd(activity: Activity, ad: AppOpenAd) = suspendCancellableCoroutine<Unit> { continuation ->
        if (appOpenAd === ad) appOpenAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                preferences.edit().putLong(KEY_LAST_APP_OPEN_SHOWN, System.currentTimeMillis()).apply()
            }

            override fun onAdDismissedFullScreenContent() {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.d(TAG, "App open show failed: ${error.message}")
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        ad.show(activity)
    }

    companion object {
        private const val TAG = "BeezeAds"
        private const val KEY_LAST_APP_OPEN_SHOWN = "last_app_open_shown"
        private const val MAX_CONCURRENT_NATIVE_ADS = 2
        private const val MAX_AD_ATTEMPTS = 3
        private const val AD_RETRY_DELAY_MS = 10_000L
        private const val NATIVE_FAILURE_SPACING_MS = 3_000L
        private const val APP_OPEN_COOLDOWN_MS = 4 * 60 * 60 * 1000L
        private const val APP_OPEN_AD_VALID_MS = 4 * 60 * 60 * 1000L
    }
}
