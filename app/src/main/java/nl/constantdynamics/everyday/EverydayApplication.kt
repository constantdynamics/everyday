package nl.constantdynamics.everyday

import android.app.Application

class EverydayApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startOnderhoud()
    }
}
