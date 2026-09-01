package ir.arsam.goldenpresident

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader
import ir.myket.billingclient.IabHelper

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var billing: IabHelper? = null
    private var billingReady = false
    private var interstitialLoader: InterstitialAdLoader? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedLoader: RewardedAdLoader? = null
    private var rewardedAd: RewardedAd? = null

    // These IDs must exactly match the product IDs created in the Myket developer panel.
    private val consumables = setOf("gp_capital_50k", "gp_capital_180k", "gp_capital_400k")
    private val nonConsumables = setOf("gp_premium", "gp_no_ads")

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = Color.rgb(7, 18, 30)
        window.navigationBarColor = Color.rgb(2, 11, 20)

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

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri != null && (uri.scheme.equals("https", true) || uri.scheme.equals("http", true))) {
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
                    // Restore permanent entitlements automatically after connecting.
                    restoreNonConsumables()
                } else {
                    callJs("window.onMyketPurchaseFailed('اتصال پرداخت مایکت برقرار نشد')")
                }
            }
        }
    }

    private fun initAds() {
        // Replace the two demo IDs in build.gradle with your production Yandex ad-unit IDs.
        interstitialLoader = InterstitialAdLoader(this)
        rewardedLoader = RewardedAdLoader(this)
        loadInterstitial()
        loadRewarded()
    }

    private fun loadInterstitial() {
        val loader = interstitialLoader ?: return
        loader.loadAd(AdRequest.Builder(BuildConfig.INTERSTITIAL_AD_UNIT_ID).build(), object : InterstitialAdLoadListener {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                ad.setAdEventListener(interstitialEvents)
            }
            override fun onAdFailedToLoad(error: com.yandex.mobile.ads.common.AdRequestError) {
                interstitialAd = null
            }
        })
    }

    private fun loadRewarded() {
        val loader = rewardedLoader ?: return
        loader.loadAd(AdRequest.Builder(BuildConfig.REWARDED_AD_UNIT_ID).build(), object : RewardedAdLoadListener {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                ad.setAdEventListener(rewardedEvents)
            }
            override fun onAdFailedToLoad(error: com.yandex.mobile.ads.common.AdRequestError) {
                rewardedAd = null
            }
        })
    }

    private val interstitialEvents = object : InterstitialAdEventListener {
        override fun onAdShown() {}
        override fun onAdFailedToShow(error: com.yandex.mobile.ads.common.AdError) { interstitialAd = null; loadInterstitial() }
        override fun onAdDismissed() { interstitialAd = null; loadInterstitial() }
        override fun onAdClicked() {}
        override fun onAdImpression(impressionData: com.yandex.mobile.ads.common.ImpressionData?) {}
    }

    private val rewardedEvents = object : RewardedAdEventListener {
        override fun onAdShown() {}
        override fun onAdFailedToShow(error: com.yandex.mobile.ads.common.AdError) { rewardedAd = null; loadRewarded(); callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()") }
        override fun onAdDismissed() { rewardedAd = null; loadRewarded() }
        override fun onAdClicked() {}
        override fun onAdImpression(impressionData: com.yandex.mobile.ads.common.ImpressionData?) {}
        override fun onRewarded(reward: com.yandex.mobile.ads.rewarded.Reward) { callJs("window.onMyketRewardedCompleted && window.onMyketRewardedCompleted()") }
    }

    private fun callJs(js: String) = runOnUiThread { if (::webView.isInitialized) webView.evaluateJavascript(js, null) }
    private fun quote(s: String?) = org.json.JSONObject.quote(s ?: "")
    private fun knownProduct(id: String) = consumables.contains(id) || nonConsumables.contains(id)

    inner class MyketBridge {
        @JavascriptInterface fun purchase(productId: String) = runOnUiThread {
            if (!knownProduct(productId)) { callJs("window.onMyketPurchaseFailed('شناسه محصول نامعتبر است')"); return@runOnUiThread }
            val helper = billing
            if (!billingReady || helper == null) { callJs("window.onMyketPurchaseFailed('مایکت آماده پرداخت نیست')"); return@runOnUiThread }
            helper.launchPurchaseFlow(this@MainActivity, productId) { result, purchase ->
                if (result?.isSuccess == true && purchase != null) {
                    val sku = purchase.sku
                    if (consumables.contains(sku)) {
                        helper.consumeAsync(purchase) { consumeResult, _ ->
                            if (consumeResult?.isSuccess == true) callJs("window.onMyketPurchaseSuccess(${quote(sku)})")
                            else callJs("window.onMyketPurchaseFailed('خرید انجام شد اما مصرف محصول تأیید نشد')")
                        }
                    } else callJs("window.onMyketPurchaseSuccess(${quote(sku)})")
                } else callJs("window.onMyketPurchaseFailed(${quote(result?.message ?: "خطای نامشخص")})")
            }
        }

        @JavascriptInterface fun restore() {
            if (!billingReady) {
                callJs("window.onMyketPurchaseFailed('مایکت آماده بازیابی خریدها نیست')")
                return
            }
            restoreNonConsumables(notifyFailure = true)
        }

        @JavascriptInterface fun showRewarded() = runOnUiThread {
            if (webViewHasNoAds()) { callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()"); return@runOnUiThread }
            val ad = rewardedAd
            if (ad == null) { loadRewarded(); callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()"); return@runOnUiThread }
            ad.show(this@MainActivity)
        }

        @JavascriptInterface fun showInterstitial() = runOnUiThread {
            if (webViewHasNoAds()) return@runOnUiThread
            val ad = interstitialAd
            if (ad != null) ad.show(this@MainActivity) else loadInterstitial()
        }
    }

    private fun restoreNonConsumables(notifyFailure: Boolean = false) {
        val helper = billing ?: return
        helper.queryInventoryAsync(false, null) { result, inventory ->
            if (result?.isSuccess == true && inventory != null) {
                nonConsumables.forEach { sku ->
                    if (inventory.hasPurchase(sku)) {
                        callJs("window.onMyketPurchaseRestored(${quote(sku)})")
                    }
                }
            } else if (notifyFailure) {
                callJs("window.onMyketPurchaseFailed('بازیابی خریدها ناموفق بود')")
            }
        }
    }

    private fun webViewHasNoAds(): Boolean = false // HTML owns the no-ads entitlement.

    override fun onDestroy() {
        billing?.let { runCatching { it.dispose() } }
        interstitialAd = null
        rewardedAd = null
        webView.removeJavascriptInterface("MyketBridge")
        webView.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
