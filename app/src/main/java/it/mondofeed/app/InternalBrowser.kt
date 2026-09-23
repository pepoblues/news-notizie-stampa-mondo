package it.mondofeed.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView

private val BrowserNavy = Color(0xFF071B3A)
private val BrowserGold = Color(0xFFFFB800)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalBrowser(url: String, onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var resolvedUrl by remember(url) { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        loading = true
        errorMessage = null
        resolvedUrl = runCatching { GoogleNewsResolver.resolve(url) }
            .getOrDefault(url)
    }

    // Sia la freccia dell'app sia il tasto Indietro chiudono l'articolo.
    // Non attraversano la cronologia Google News.
    fun closeArticle() {
        webView?.stopLoading()
        onClose()
    }

    BackHandler { closeArticle() }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrowserNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = ::closeArticle) {
                        Text("‹", color = Color.White)
                    }
                },
                title = {
                    Text(
                        text = "MondoFeed",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = {
                        loading = true
                        errorMessage = null
                        webView?.reload()
                    }) {
                        Text("↻", color = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            resolvedUrl?.let { articleUrl ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webView = this

                            CookieManager.getInstance().setAcceptCookie(true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadsImagesAutomatically = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                builtInZoomControls = true
                                displayZoomControls = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString = userAgentString.replace("; wv", "")
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = openInside(view, request?.url?.toString())

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    destination: String?
                                ): Boolean = openInside(view, destination)

                                private fun openInside(
                                    view: WebView?,
                                    destination: String?
                                ): Boolean {
                                    val target = destination.orEmpty()

                                    if (target.contains("consent.google.com", ignoreCase = true)) {
                                        loading = false
                                        errorMessage = "Collegamento Google non disponibile direttamente."
                                        return true
                                    }

                                    if (target.startsWith("http://") || target.startsWith("https://")) {
                                        loading = true
                                        view?.loadUrl(target)
                                    }

                                    // Impedisce l'apertura di Chrome o di applicazioni esterne.
                                    return true
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    loading = true
                                    errorMessage = null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    view?.clearHistory()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        loading = false
                                        errorMessage = "Impossibile caricare l'articolo."
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean = false
                            }

                            loadUrl(articleUrl)
                        }
                    },
                    update = { currentWebView ->
                        webView = currentWebView
                        if (currentWebView.url != articleUrl) {
                            currentWebView.loadUrl(articleUrl)
                        }
                    }
                )
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = BrowserGold
                )
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    color = Color.Gray
                )
            }
        }
    }
}
