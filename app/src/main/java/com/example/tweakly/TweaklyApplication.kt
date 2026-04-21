package com.example.tweakly

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TweaklyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
