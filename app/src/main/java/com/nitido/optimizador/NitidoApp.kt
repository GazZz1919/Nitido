package com.nitido.optimizador

import android.app.Application
import com.nitido.optimizador.work.MaintenanceScheduler
import com.nitido.optimizador.work.Notifier

class NitidoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        MaintenanceScheduler.apply(this)
    }
}
