package nl.constantdynamics.dagelijks

import android.app.Application
import java.util.Locale

class DagelijksApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The app is Dutch only: dates and numbers are always written the Dutch way,
        // whatever language the phone is set to.
        Locale.setDefault(DUTCH)
        container = AppContainer(this)
    }

    companion object {
        val DUTCH: Locale = Locale.forLanguageTag("nl-NL")
    }
}
