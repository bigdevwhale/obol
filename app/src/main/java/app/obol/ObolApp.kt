package app.obol

import android.app.Application
import android.content.Context
import app.obol.data.DemoSeed
import app.obol.data.ObolDao
import app.obol.data.ObolDatabase
import app.obol.data.Presets
import app.obol.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val db = ObolDatabase.create(context)
    val dao: ObolDao get() = db.dao()
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget so a write survives the screen being closed right after tapping save. */
    fun write(block: suspend ObolDao.() -> Unit) {
        scope.launch { dao.block() }
    }
}

class ObolApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(container.dao, container.settings)
            else Presets.seedIfEmpty(container.dao)
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as ObolApp).container
