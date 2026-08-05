package com.yamahub.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("yamahub", Context.MODE_PRIVATE)

    var lastDeviceAddress: String?
        get() = sp.getString("last_device_address", null)
        set(v) = sp.edit().putString("last_device_address", v).apply()

    var lastDeviceName: String?
        get() = sp.getString("last_device_name", null)
        set(v) = sp.edit().putString("last_device_name", v).apply()

    var controlOrder: String
        get() = sp.getString("control_order", "hazard,left,right") ?: "hazard,left,right"
        set(v) = sp.edit().putString("control_order", v).apply()

    var lastSettingsTab: Int
        get() = sp.getInt("last_settings_tab", 0)
        set(v) = sp.edit().putInt("last_settings_tab", v.coerceIn(0, 3)).apply()

    /** Próg (w ms) oddzielający short press od long press. Domyślnie 400 ms. */
    var shortPressThresholdMs: Int
        get() = sp.getInt("short_press_threshold_ms", 400)
        set(v) = sp.edit().putInt("short_press_threshold_ms", v.coerceIn(100, 2000)).apply()
}