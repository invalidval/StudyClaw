package com.zlearn

import android.app.Application
import com.zlearn.utils.BleShareTransport
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        BleShareTransport.init(this)
    }

}
