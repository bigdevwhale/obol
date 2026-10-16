package app.obol.watch

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.obol.R
import app.obol.container
import app.obol.ui.components.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * The oath by the Styx. While it holds, Obol is a device administrator, so it can't be
 * uninstalled, and the gate's settings are locked. Breaking it is possible, but it stays on record.
 */
class Styx : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val s = kotlinx.coroutines.runBlocking { context.container.settings.now() }
        if (!s.oath()) return null
        val until = Instant.ofEpochMilli(s.oathUntil).atZone(ZoneId.systemDefault())
        return context.getString(R.string.styx_warning, "%02d.%02d %s".format(until.dayOfMonth, until.monthValue, Fmt.hm(until.hour * 60 + until.minute)))
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val c = context.container
        c.scope.launch {
            if (c.settings.now().oath()) c.settings.update { it.copy(oathBrokenAt = System.currentTimeMillis(), oathUntil = 0) }
        }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, Styx::class.java)

        fun active(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        /** The system screen that asks to make Obol an administrator. */
        fun request(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.styx_explanation))

        /** Lets go of the administrator rights once the oath is over. */
        fun release(context: Context) {
            if (active(context)) context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
        }
    }
}
