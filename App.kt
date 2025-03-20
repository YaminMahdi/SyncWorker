package com.dcp.android.utils

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

class App : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
    }

    override val workManagerConfiguration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
}

