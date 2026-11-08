package com.example.bluefamily

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Store.load(this)
        Drive.watch(this)
        if (Store.settings.consented) Drive.start(this)
    }
}
