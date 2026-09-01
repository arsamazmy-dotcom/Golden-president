package ir.arsam.goldenpresident

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.MobileAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import ir.myket.billingclient.IabHelper

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var billing: IabHelper? = null
    private var billingReady = false
    private var interstitialLoader: InterstitialAdLoader? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedLoader: RewardedAdLoader? = null
    private var rewardedAd: RewardedAd? = null
    private var noAds = false

    // These IDs must match the products created in the Myket developer panel.
    private val consumables = setOf("gp_capital_50k", "gp_capital_180k", "gp_capital_400k")
    private val nonConsumables = setOf("gp_premium", "gp_no_ads")

    private val prefs by lazy { getSharedPreferences("golden_president", MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = Color.rgb(7, 18, 30)
        window.navigationBarColor = Color.rgb(2, 11, 20)

        noAds = prefs.getBoolean("no_ads", false)

        webView = WebView(this)
        setContentView(webView)
        WebView.setWebContentsDebuggingEnabled(false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            textZoom = 100
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }
                return false
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(MyketBridge(), "MyketBridge")
        webView.loadUrl("https://appassets.androidplatform.net/assets/game.html")

        initMyketBilling()
        initAds()
    }

    private fun initMyketBilling() {
        billing = IabHelper(this, BuildConfig.MYKET_PUBLIC_KEY).also { helper ->
            helper.enableDebugLogging(false, "GoldenPresidentMyket")
            helper.startSetup { result ->
                billingReady = result?.isSuccess == true
                if (billingReady) {
                    restoreNonConsumables()
                } else {
                    callJs("window.onMyketPurchaseFailed('اتصال پرداخت مایکت برقرار نشد')")
                }
            }
        }
    }

    private fun initAds() {
        // Yandex SDK 8 requires initialization before loading ads.
        MobileAds.initialize(this) {
            runOnUiThread {
                if (!noAds) {
                    interstitialLoader = InterstitialAdLoader(this)
                    rewardedLoader = RewardedAdLoader(this)
                    loadInterstitial()
                    loadRewarded()
                }
            }
        }
    }

    private fun loadInterstitial() {
        if (noAds) return
        val loader = interstitialLoader ?: return
        loader.loadAd(
            AdRequest.Builder(BuildConfig.INTERSTITIAL_AD_UNIT_ID).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.setAdEventListener(interstitialEvents)
                }

                override fun onAdFailedToLoad(error: com.yandex.mobile.ads.common.AdRequestError) {
                    interstitialAd = null
                    // Do not immediately retry here; Yandex recommends limiting retries.
                }
            }
        )
    }

    private fun loadRewarded() {
        if (noAds) return
        val loader = rewardedLoader ?: return
        loader.loadAd(
            AdRequest.Builder(BuildConfig.REWARDED_AD_UNIT_ID).build(),
            object : RewardedAdLoadListener {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    ad.setAdEventListener(rewardedEvents)
                }

                override fun onAdFailedToLoad(error: com.yandex.mobile.ads.common.AdRequestError) {
                    rewardedAd = null
                    // Do not immediately retry here; Yandex recommends limiting retries.
                }
            }
        )
    }

    private val interstitialEvents = object : InterstitialAdEventListener {
        override fun onAdShown() = Unit

        override fun onAdFailedToShow(error: com.yandex.mobile.ads.common.AdError) {
            interstitialAd?.setAdEventListener(null)
            interstitialAd = null
            loadInterstitial()
        }

        override fun onAdDismissed() {
            interstitialAd?.setAdEventListener(null)
            interstitialAd = null
            loadInterstitial()
        }

        override fun onAdClicked() = Unit
        override fun onAdImpression(impressionData: com.yandex.mobile.ads.common.ImpressionData?) = Unit
    }

    private val rewardedEvents = object : RewardedAdEventListener {
        override fun onAdShown() = Unit

        override fun onAdFailedToShow(error: com.yandex.mobile.ads.common.AdError) {
            rewardedAd?.setAdEventListener(null)
            rewardedAd = null
            loadRewarded()
            callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()")
        }

        override fun onAdDismissed() {
            rewardedAd?.setAdEventListener(null)
            rewardedAd = null
            loadRewarded()
        }

        override fun onAdClicked() = Unit
        override fun onAdImpression(impressionData: com.yandex.mobile.ads.common.ImpressionData?) = Unit

        override fun onRewarded(reward: com.yandex.mobile.ads.rewarded.Reward) {
            callJs("window.onMyketRewardedCompleted && window.onMyketRewardedCompleted()")
        }
    }

    private fun callJs(js: String) {
        runOnUiThread {
            if (::webView.isInitialized) webView.evaluateJavascript(js, null)
        }
    }

    private fun quote(value: String?) = org.json.JSONObject.quote(value ?: "")
    private fun knownProduct(id: String) = consumables.contains(id) || nonConsumables.contains(id)

    inner class MyketBridge {
        @JavascriptInterface
        fun purchase(productId: String) = runOnUiThread {
            if (!knownProduct(productId)) {
                callJs("window.onMyketPurchaseFailed('شناسه محصول نامعتبر است')")
                return@runOnUiThread
            }
            val helper = billing
            if (!billingReady || helper == null) {
                callJs("window.onMyketPurchaseFailed('مایکت آماده پرداخت نیست')")
                return@runOnUiThread
            }

            helper.launchPurchaseFlow(this@MainActivity, productId) { result, purchase ->
                if (result?.isSuccess == true && purchase != null) {
                    val sku = purchase.sku
                    if (consumables.contains(sku)) {
                        helper.consumeAsync(purchase) { consumeResult, _ ->
                            if (consumeResult?.isSuccess == true) {
                                callJs("window.onMyketPurchaseSuccess(${quote(sku)})")
                            } else {
                                callJs("window.onMyketPurchaseFailed('خرید انجام شد اما مصرف محصول تأیید نشد')")
                            }
                        }
                    } else {
                        if (sku == "gp_no_ads" || sku == "gp_premium") {
                            setNoAds(true)
                        }
                        callJs("window.onMyketPurchaseSuccess(${quote(sku)})")
                    }
                } else {
                    callJs("window.onMyketPurchaseFailed(${quote(result?.message ?: "خطای نامشخص")})")
                }
            }
        }

        @JavascriptInterface
        fun restore() {
            runOnUiThread {
                if (!billingReady) {
                    callJs("window.onMyketPurchaseFailed('مایکت آماده بازیابی خریدها نیست')")
                    return@runOnUiThread
                }
                restoreNonConsumables(notifyFailure = true)
            }
        }

        @JavascriptInterface
        fun showRewarded() = runOnUiThread {
            if (noAds) {
                callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()")
                return@runOnUiThread
            }
            val ad = rewardedAd
            if (ad == null) {
                callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()")
                return@runOnUiThread
            }
            ad.show(this@MainActivity)
        }

        @JavascriptInterface
        fun showInterstitial() = runOnUiThread {
            if (noAds) return@runOnUiThread
            interstitialAd?.show(this@MainActivity) ?: loadInterstitial()
        }
    }

    private fun restoreNonConsumables(notifyFailure: Boolean = false) {
        val helper = billing ?: return
        helper.queryInventoryAsync(false, null) { result, inventory ->
            if (result?.isSuccess == true && inventory != null) {
                nonConsumables.forEach { sku ->
                    if (inventory.hasPurchase(sku)) {
                        if (sku == "gp_no_ads" || sku == "gp_premium") setNoAds(true)
                        callJs("window.onMyketPurchaseRestored(${quote(sku)})")
                    }
                }
            } else if (notifyFailure) {
                callJs("window.onMyketPurchaseFailed('بازیابی خریدها ناموفق بود')")
            }
        }
    }

    private fun setNoAds(enabled: Boolean) {
        noAds = enabled
        prefs.edit().putBoolean("no_ads", enabled).apply()
        if (enabled) {
            interstitialAd?.setAdEventListener(null)
            rewardedAd?.setAdEventListener(null)
            interstitialAd = null
            rewardedAd = null
        }
    }

    override fun onDestroy() {
        billing?.let { runCatching { it.dispose() } }
        interstitialAd?.setAdEventListener(null)
        rewardedAd?.setAdEventListener(null)
        interstitialLoader = null
        rewardedLoader = null
        interstitialAd = null
        rewardedAd = null
        webView.removeJavascriptInterface("MyketBridge")
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
