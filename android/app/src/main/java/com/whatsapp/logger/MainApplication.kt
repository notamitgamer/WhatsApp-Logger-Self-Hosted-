package com.whatsapp.logger

import android.app.Application
import com.whatsapp.logger.di.AppContainer

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.getInstance(this)
    }
}