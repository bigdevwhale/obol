package app.obol

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * The language chosen in the app. Activities get it from AppCompat; the accessibility service,
 * notifications and the demo seed have to ask for it themselves.
 */
object Lang {
    fun locale(context: Context): Locale =
        AppCompatDelegate.getApplicationLocales()[0] ?: context.resources.configuration.locales[0] ?: Locale.getDefault()

    /** A context whose resources speak the app's language. */
    fun wrap(context: Context): Context {
        val chosen = AppCompatDelegate.getApplicationLocales()[0] ?: return context
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(chosen)) }
        return context.createConfigurationContext(config)
    }
}
