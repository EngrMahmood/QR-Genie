package com.qrgenie.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Real AdMob App ID and ad unit IDs from the AdMob console (app: QR Genie).
 */
object AdIds {
    const val APP_ID = "ca-app-pub-8023538033112382~4535062796"
    const val BANNER_UNIT_ID = "ca-app-pub-8023538033112382/8282736119"
    const val INTERSTITIAL_UNIT_ID = "ca-app-pub-8023538033112382/7828988702"
}

/**
 * Shows an interstitial at most once every INTERSTITIAL_FREQUENCY triggers (scan
 * results / QR generations) instead of every single time - keeps the app usable and
 * avoids the kind of ad density Play Console flags during review.
 */
object AdsManager {
    private const val TAG = "AdsManager"
    private const val PREFS_NAME = "ads_prefs"
    private const val KEY_TRIGGER_COUNT = "interstitial_trigger_count"
    private const val INTERSTITIAL_FREQUENCY = 3

    @Volatile private var initialized = false
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var isLoadingInterstitial = false
    private lateinit var consentInformation: ConsentInformation

    /** Call from an Activity (e.g. MainActivity.onCreate) so the UMP form has a window to attach to. */
    fun initializeAndRequestConsent(activity: Activity) {
        if (initialized) {
            if (interstitialAd == null && !isLoadingInterstitial) loadInterstitial(activity)
            return
        }
        initialized = true

        val params = ConsentRequestParameters.Builder().build()

        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.d(TAG, "Consent form error: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        startMobileAds(activity)
                    }
                }
            },
            { error -> Log.d(TAG, "Consent info update failed: ${error.message}") }
        )
    }

    private fun startMobileAds(context: Context) {
        MobileAds.initialize(context) {
            loadInterstitial(context)
        }
    }

    private fun loadInterstitial(context: Context) {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            context,
            AdIds.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
                    Log.d(TAG, "Interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Increments the trigger counter and shows the preloaded interstitial only every
     * [INTERSTITIAL_FREQUENCY]th call. Always invokes [onDone] (immediately if no ad
     * is shown) so callers can chain navigation/UI updates regardless of ad outcome.
     */
    fun maybeShowInterstitial(activity: Activity, onDone: () -> Unit = {}) {
        val prefs = prefs(activity)
        val count = prefs.getInt(KEY_TRIGGER_COUNT, 0) + 1
        prefs.edit().putInt(KEY_TRIGGER_COUNT, count).apply()

        val ad = interstitialAd
        if (count % INTERSTITIAL_FREQUENCY != 0 || ad == null) {
            onDone()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial(activity)
                onDone()
            }
        }
        interstitialAd = null
        ad.show(activity)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
