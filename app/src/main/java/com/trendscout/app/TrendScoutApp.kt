package com.trendscout.app

import android.app.Application
import com.trendscout.app.notify.Notifier

class TrendScoutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannel(this)
    }
}
