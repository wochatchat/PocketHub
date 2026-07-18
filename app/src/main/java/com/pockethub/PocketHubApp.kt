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
 * Application base that supplies a Coil ImageLoader with SVG decoding, so README badges / logos /
 * charts (.svg from shields.io etc.) render instead of falling back to the broken-image placeholder.
 *
 * Implemented on an abstract base class rather than directly on the @HiltAndroidApp class: Hilt's
 * KSP processor mishandles a Coil `SingletonImageLoader.Factory` supertype on the annotated class
 * (reports "base class must extend Application. Found: kotlin.Any"). Moving the interface onto a
 * regular Application subclass keeps Hilt happy while `applicationContext as? Factory` still
 * resolves to this loader at runtime.
 */
abstract class SvgImageLoaderApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .build()
}

/**
 * Application entry point. Provides WorkManager configuration using [HiltWorkerFactory]
 * so that [com.pockethub.data.remote.NotifPollWorker] can receive injected dependencies.
 */
@HiltAndroidApp
class PocketHubApp : SvgImageLoaderApp(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
