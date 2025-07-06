package com.xyna.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class XynaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initChaquopy()
        com.xyna.app.core.XynaCoreBrain.initialize(this)
    }

    private fun initChaquopy() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
