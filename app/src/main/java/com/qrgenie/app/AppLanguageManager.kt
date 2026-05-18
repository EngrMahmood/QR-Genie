package com.qrgenie.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    private const val DEFAULT_LANGUAGE_TAG = "en"

    fun getSavedLanguageTag(context: Context): String {
        return prefs(context).getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG
    }

    fun saveLanguageTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE_TAG, tag).apply()
    }

    fun wrapContext(base: Context): ContextWrapper {
        val tag = getSavedLanguageTag(base)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return ContextWrapper(base.createConfigurationContext(config))
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

open class LocalizedComponentActivity : androidx.activity.ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }
}

