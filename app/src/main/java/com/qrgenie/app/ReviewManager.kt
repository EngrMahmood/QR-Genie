package com.qrgenie.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Triggers Google Play's in-app review flow after a handful of successful scans/generations
 * instead of relying only on the manual "Rate QR Genie" Settings link. The Play Core API
 * enforces its own internal quota (shows the real dialog only a limited number of times per
 * user regardless of how often we call it), so we just need to avoid asking on every single
 * action - once past PROMPT_THRESHOLD successes we stop asking again this install.
 */
object ReviewManager {
    private const val TAG = "ReviewManager"
    private const val PREFS_NAME = "review_prefs"
    private const val KEY_SUCCESS_COUNT = "success_count"
    private const val KEY_PROMPTED = "already_prompted"
    private const val PROMPT_THRESHOLD = 3

    fun maybePromptReview(activity: Activity) {
        val prefs = prefs(activity)
        if (prefs.getBoolean(KEY_PROMPTED, false)) return

        val count = prefs.getInt(KEY_SUCCESS_COUNT, 0) + 1
        prefs.edit().putInt(KEY_SUCCESS_COUNT, count).apply()
        if (count < PROMPT_THRESHOLD) return

        prefs.edit().putBoolean(KEY_PROMPTED, true).apply()

        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                } else {
                    Log.d(TAG, "Review flow request failed: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Review flow error: ${e.message}")
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
