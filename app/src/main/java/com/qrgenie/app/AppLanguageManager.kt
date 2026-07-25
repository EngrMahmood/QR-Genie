package com.qrgenie.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguageManager {
    private const val DEFAULT_LANGUAGE_TAG = "en"

    /**
     * AppCompatDelegate is the single source of truth for the selected language -
     * backed by the platform LocaleManager on API 33+ and AppCompat's own storage
     * below that. Previously this app *also* kept a manually-applied Configuration
     * override via attachBaseContext, sourced from a separate SharedPreferences copy.
     * On API 33+ with android:localeConfig declared, the OS manages per-app locale
     * itself; the manual override raced with it (attachBaseContext ran before the
     * OS's own locale change had propagated back through getApplicationLocales()),
     * so the app kept reverting to English. Removing the manual override and
     * relying purely on setApplicationLocales()/getApplicationLocales() avoids that
     * race entirely.
     */
    fun getCurrentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty) locales[0]?.toLanguageTag() ?: DEFAULT_LANGUAGE_TAG else DEFAULT_LANGUAGE_TAG
    }

    fun applyLanguageTag(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}

/**
 * Must extend AppCompatActivity (not plain ComponentActivity): AppCompatDelegate's
 * per-app-language mechanism - both the API 33+ framework LocaleManager bridge and
 * the automatic Activity recreation on locale change - only engages for Activities
 * that create an AppCompatDelegate instance. Without this, setApplicationLocales()
 * silently no-ops (confirmed on-device: `cmd locale get-app-locales` stayed empty
 * after selecting a language). Theme.QRAPP was updated to extend
 * Theme.AppCompat.Light.NoActionBar to support this (AppCompatActivity requires an
 * AppCompat/MaterialComponents theme or it throws at runtime).
 */
open class LocalizedComponentActivity : androidx.appcompat.app.AppCompatActivity()
