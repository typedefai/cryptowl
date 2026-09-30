package com.typedefai.cryptowl.settings

import android.content.Context

/**
 * Non-sensitive app settings (design's `global_prefs.xml`): auto-lock timeout
 * and FLAG_SECURE. Backed by SharedPreferences — no secrets live here.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoLockTimeoutMs: Long
        get() = prefs.getLong(KEY_AUTO_LOCK_TIMEOUT, AutoLockPolicy.DEFAULT)
        set(value) = prefs.edit().putLong(KEY_AUTO_LOCK_TIMEOUT, value).apply()

    var flagSecure: Boolean
        get() = prefs.getBoolean(KEY_FLAG_SECURE, false)
        set(value) = prefs.edit().putBoolean(KEY_FLAG_SECURE, value).apply()

    private companion object {
        const val PREFS_NAME = "cryptowl.settings"
        const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout_ms"
        const val KEY_FLAG_SECURE = "flag_secure"
    }
}
