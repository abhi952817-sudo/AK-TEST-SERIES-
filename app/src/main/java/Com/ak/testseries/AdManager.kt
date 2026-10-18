package Com.ak.testseries

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
// import com.google.android.gms.ads.AdRequest
// import com.google.android.gms.ads.LoadAdError
// import com.google.android.gms.ads.appopen.AppOpenAd
// import com.google.android.gms.ads.interstitial.InterstitialAd
// import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
// import com.google.android.gms.ads.FullScreenContentCallback
// import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AdManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
    
    // Default Test IDs
    private var appOpenAdId = "ca-app-pub-3940256099942544/3419835294"
    private var interstitialAdId = "ca-app-pub-3940256099942544/1033173712"

    init {
        // Automatically try to fetch latest IDs on initialization
        // fetchAdUnitIds()
    }

    /**
     * 2. Dynamic Ad Unit IDs: Fetches from Firestore
     * Uses fallback test IDs if it fails.
     */
    fun fetchAdUnitIds() {
        /*
        // Mocked Firebase Firestore Fetch
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("admin_settings").document("ad_units")
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        document.getString("appOpenAdId")?.let { appOpenAdId = it }
                        document.getString("interstitialAdId")?.let { interstitialAdId = it }
                        Log.d("AdManager", "Successfully fetched Ad IDs from Firestore")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("AdManager", "Failed to fetch Ad IDs, using fallback test IDs", exception)
                }
        } catch (e: Exception) {
            Log.e("AdManager", "Firebase not initialized or error", e)
        }
        */
        Log.d("AdManager", "Mock: Fetched Ad IDs. AppOpen=$appOpenAdId, Interstitial=$interstitialAdId")
    }

    /**
     * 3. AdMob Logic & State Management
     * App Open Ad Logic: max 3 times/day, 6 hour gap
     */
    fun shouldShowAppOpenAd(): Boolean {
        val lastAdTime = prefs.getLong("last_app_open_ad_time", 0)
        val dailyAdCount = prefs.getInt("daily_app_open_ad_count", 0)
        val lastResetTime = prefs.getLong("last_daily_reset_time", 0)
        val currentTime = System.currentTimeMillis()

        // Reset daily counter at midnight (or after 24 hours for simplicity here)
        if (currentTime - lastResetTime > TimeUnit.DAYS.toMillis(1)) {
            prefs.edit()
                .putInt("daily_app_open_ad_count", 0)
                .putLong("last_daily_reset_time", currentTime)
                .apply()
        }

        val updatedDailyAdCount = prefs.getInt("daily_app_open_ad_count", 0)
        val timeDifference = currentTime - lastAdTime

        // If daily count < 3 AND time diff > 6 hours
        if (updatedDailyAdCount < 3 && timeDifference > TimeUnit.HOURS.toMillis(6)) {
            return true
        }
        return false
    }

    fun onAppOpenAdShown() {
        val currentCount = prefs.getInt("daily_app_open_ad_count", 0)
        prefs.edit()
            .putLong("last_app_open_ad_time", System.currentTimeMillis())
            .putInt("daily_app_open_ad_count", currentCount + 1)
            .apply()
    }

    fun showAppOpenAdIfEligible() {
        if (shouldShowAppOpenAd()) {
            Log.d("AdManager", "Showing App Open Ad (Simulated)")
            // Actual AdMob Code:
            // val request = AdRequest.Builder().build()
            // AppOpenAd.load(context, appOpenAdId, request, AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, ...)
            onAppOpenAdShown()
        } else {
            Log.d("AdManager", "App Open Ad not eligible (Rate limited)")
        }
    }

    /**
     * Test Start Ad Logic
     * Shows an interstitial ad and invokes callback when dismissed.
     */
    fun showTestStartInterstitialAd(onAdDismissed: () -> Unit) {
        Log.d("AdManager", "Loading Interstitial Ad for Test Start (Simulated)")
        // Simulate ad delay and dismissal
        onAdDismissed()

        /* Actual AdMob Implementation:
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, interstitialAdId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdManager", "Interstitial Ad failed to load: ${adError.message}")
                onAdDismissed() // Proceed anyway if ad fails
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdManager", "Interstitial Ad dismissed")
                        onAdDismissed()
                    }
                    override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                        onAdDismissed()
                    }
                }
                // Requires Activity context to show, so you'd pass activity to this method
                // interstitialAd.show(activity)
            }
        })
        */
    }
}
