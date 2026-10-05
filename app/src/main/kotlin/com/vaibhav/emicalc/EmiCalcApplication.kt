package com.vaibhav.emicalc

import android.app.Application
import com.vaibhav.emicalc.di.AppContainer

/**
 * Holds the single [AppContainer].
 *
 * Dependency injection is done by hand rather than with Hilt: the graph is half a dozen
 * objects, and a manual container keeps the build free of another annotation processor.
 */
class EmiCalcApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
