package com.sabimoni

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sabimoni.core.reminder.ObligationScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SabiMoniApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var obligations: ObligationScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Armed here rather than from a screen: a recurring contribution must keep rolling
        // forward and keep nagging whether or not the user has opened the Groups tab. The
        // enqueue is idempotent, so doing it on every start is the point (ADR-0029).
        obligations.ensureDailyCheck()
    }
}
