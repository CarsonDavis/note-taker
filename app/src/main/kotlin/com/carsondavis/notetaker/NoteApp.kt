package com.carsondavis.notetaker

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NoteApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
