package com.qrgenie.app

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeManager {
    fun getCurrentMode(): ThemeMode = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> ThemeMode.DARK
        AppCompatDelegate.MODE_NIGHT_NO -> ThemeMode.LIGHT
        else -> ThemeMode.SYSTEM
    }

    fun applyMode(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
