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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import app.obol.domain.Temple
import app.obol.widget.PurseWidget

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

@OptIn(kotlinx.coroutines.FlowPreview::class)
class ObolApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(container.dao, container.settings, Lang.locale(this@ObolApp).language == "ru")
            else Presets.seedIfEmpty(container.dao)
            Temple(container.dao, container.settings).accrue()
        }
        // Every change of the purse redraws the widget, as long as Charon keeps the process alive.
        container.scope.launch {
            container.dao.observeBalance().debounce(2_000).collect { PurseWidget.refresh(this@ObolApp) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as ObolApp).container
