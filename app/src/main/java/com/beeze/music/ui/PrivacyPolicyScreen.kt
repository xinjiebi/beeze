package com.beeze.music.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.beeze.music.DetailPage
import com.beeze.music.PlayerViewModel
import com.beeze.music.R
import com.beeze.music.data.AppLocale

private const val PRIVACY_POLICY_URL_ZH = "https://xinjiebi.github.io/beeze/privacy/"
private const val PRIVACY_POLICY_URL_EN = "https://xinjiebi.github.io/beeze/privacy/en/"

@Composable
fun PrivacyPolicyScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    val goBack: () -> Unit = {
        if (webView?.canGoBack() == true) webView?.goBack()
        else viewModel.openDetail(DetailPage.About)
    }
    BackHandler(onBack = goBack)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AboutTopBar(tr(R.string.privacy_policy), goBack)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webView = this
                    loadUrl(if (AppLocale.isSimplifiedChinese(context)) PRIVACY_POLICY_URL_ZH else PRIVACY_POLICY_URL_EN)
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}
