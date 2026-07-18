package com.pockethub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.SingletonImageLoader
import coil.decode.SvgDecoder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Provides WorkManager configuration using [HiltWorkerFactory]
 * so that [com.pockethub.data.remote.NotifPollWorker] can receive injected dependencies.
 */
@HiltAndroidApp
class PocketHubApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Coil ImageLoader with SVG support so README badges / logos / charts (shields.io .svg,
    // repo .svg assets) decode instead of falling back to the broken-image placeholder.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .build()
}
