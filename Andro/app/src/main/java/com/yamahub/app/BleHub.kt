package com.yamahub.app

import android.content.Context

object BleHub {
    @Volatile
    private var instance: BleManager? = null

    fun manager(context: Context): BleManager {
        return instance ?: synchronized(this) {
            instance ?: BleManager(context.applicationContext).also { instance = it }
        }
    }
}