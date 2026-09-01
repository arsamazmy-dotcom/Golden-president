package ir.arsam.goldenpresident

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.MobileAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.IabResult
import ir.myket.billingclient.util.Inventory
import ir.myket.billingclient.util.Purchase

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    private var billing: IabHelper? = null
    private var billingReady = false
    private var noAds = false

    private val consumables = setOf("gp_capital_50k", "gp_capital_180k", "gp_capital_400k")
    private val nonConsumables = setOf("gp_no_ads", "gp_premium")

    private var interstitialLoader: InterstitialAdLoader? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedLoader: RewardedAdLoader? = null
    private var rewardedAd: RewardedAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setupWebView()
        setupBilling()
        setupAds()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(MyketBridge(), "MyketBridge")
        webView.addJavascriptInterface(MyketBridge(), "Android")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/game.html")
    }

    private fun setupBilling() {
        billing = IabHelper(this, BuildConfig.MYKET_PUBLIC_KEY).also { helper ->
            helper.enableDebugLogging(false)
            helper.startSetup(object : IabHelper.OnIabSetupFinishedListener {
                override fun onIabSetupFinished(result: IabResult) {
                    if (result.isFailure) {
                        billingReady = false
                        return
                    }
                    billingReady = true
                    restorePurchases(false)
                }
            })
        }
    }

    private fun restorePurchases(notifyFailure: Boolean) {
        val helper = billing ?: return
        if (!billingReady) {
            if (notifyFailure) callJs("window.onMyketPurchaseFailed('مایکت آماده نیست')")
            return
        }
        val skus = ArrayList<String>().apply { addAll(nonConsumables) }
        helper.queryInventoryAsync(true, skus, object : IabHelper.QueryInventoryFinishedListener {
            override fun onQueryInventoryFinished(result: IabResult, inventory: Inventory) {
                if (result.isFailure) {
                    if (notifyFailure) callJs("window.onMyketPurchaseFailed(${quote(result.toString())})")
                    return
                }
                var restoredNoAds = false
                for (id in nonConsumables) {
                    if (inventory.getPurchase(id) != null) {
                        restoredNoAds = true
                        callJs("window.onMyketPurchaseRestored(${quote(id)})")
                    }
                }
                if (restoredNoAds) setNoAds(true)
            }
        })
    }

    private val purchaseListener = object : IabHelper.OnIabPurchaseFinishedListener {
        override fun onIabPurchaseFinished(result: IabResult, purchase: Purchase?) {
            if (result.isFailure || purchase == null) {
                callJs("window.onMyketPurchaseFailed(${quote(result.toString())})")
                return
            }
            val sku = purchase.sku
            if (consumables.contains(sku)) {
                billing?.consumeAsync(purchase, object : IabHelper.OnConsumeFinishedListener {
                    override fun onConsumeFinished(purchase: Purchase, result: IabResult) {
                        if (result.isSuccess) {
                            callJs("window.onMyketPurchaseSuccess(${quote(sku)})")
                        } else {
                            callJs("window.onMyketPurchaseFailed(${quote(result.toString())})")
                        }
                    }
                })
            } else {
                if (sku == "gp_no_ads" || sku == "gp_premium") setNoAds(true)
                callJs("window.onMyketPurchaseSuccess(${quote(sku)})")
            }
        }
    }

    private fun setupAds() {
        MobileAds.initialize(this) {
            runOnUiThread {
                if (!noAds) {
                    interstitialLoader = InterstitialAdLoader(this).apply {
                        setAdLoadListener(object : InterstitialAdLoadListener {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                interstitialAd = ad
                            }

                            override fun onAdFailedToLoad(error: AdRequestError) {
                                interstitialAd = null
                            }
                        })
                    }
                    rewardedLoader = RewardedAdLoader(this)
                    loadInterstitial()
                    loadRewarded()
                }
            }
        }
    }

    private fun loadInterstitial() {
        if (noAds) return
        interstitialLoader?.loadAd(AdRequest.Builder(BuildConfig.INTERSTITIAL_AD_UNIT_ID).build())
    }

    private fun loadRewarded() {
        if (noAds) return
        rewardedLoader?.loadAd(
            AdRequest.Builder(BuildConfig.REWARDED_AD_UNIT_ID).build(),
            object : RewardedAdLoadListener {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun showInterstitial() {
        if (noAds) return
        val ad = interstitialAd ?: run {
            loadInterstitial()
            return
        }
        ad.setAdEventListener(object : InterstitialAdEventListener {
            override fun onAdShown() = Unit
            override fun onAdFailedToShow(error: AdError) {
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
            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        })
        ad.show(this)
    }

    private fun showRewarded() {
        if (noAds) {
            callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()")
            return
        }
        val ad = rewardedAd ?: run {
            callJs("window.onMyketRewardedFailed && window.onMyketRewardedFailed()")
            loadRewarded()
            return
        }
        ad.setAdEventListener(object : RewardedAdEventListener {
            override fun onAdShown() = Unit
            override fun onAdFailedToShow(error: AdError) {
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
            override fun onAdImpression(impressionData: ImpressionData?) = Unit
            override fun onRewarded(reward: Reward) {
                callJs("window.onMyketRewardedCompleted && window.onMyketRewardedCompleted()")
            }
        })
        ad.show(this)
    }

    private fun setNoAds(value: Boolean) {
        noAds = value
        if (value) {
            interstitialAd?.setAdEventListener(null)
            interstitialAd = null
            rewardedAd?.setAdEventListener(null)
            rewardedAd = null
        }
    }

    private fun callJs(js: String) {
        runOnUiThread {
            if (::webView.isInitialized) webView.evaluateJavascript(js, null)
        }
    }

    private fun quote(value: String?) = org.json.JSONObject.quote(value ?: "")

    inner class MyketBridge {
        @JavascriptInterface
        fun purchase(productId: String) {
            runOnUiThread {
                if (!nonConsumables.contains(productId) && !consumables.contains(productId)) {
                    callJs("window.onMyketPurchaseFailed('شناسه محصول نامعتبر است')")
                    return@runOnUiThread
                }
                val helper = billing
                if (!billingReady || helper == null) {
                    callJs("window.onMyketPurchaseFailed('مایکت آماده پرداخت نیست')")
                    return@runOnUiThread
                }
                helper.launchPurchaseFlow(this@MainActivity, productId, purchaseListener, "")
            }
        }

        @JavascriptInterface
        fun restore() {
            runOnUiThread { restorePurchases(true) }
        }

        @JavascriptInterface
        fun showRewarded() {
            runOnUiThread { showRewarded() }
        }

        @JavascriptInterface
        fun showInterstitial() {
            runOnUiThread { showInterstitial() }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        interstitialLoader?.setAdLoadListener(null)
        interstitialAd?.setAdEventListener(null)
        rewardedAd?.setAdEventListener(null)
        interstitialLoader = null
        rewardedLoader = null
        interstitialAd = null
        rewardedAd = null
        billing?.dispose()
        billing = null
        webView.destroy()
        super.onDestroy()
    }
}
