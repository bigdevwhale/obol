package app.obol.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.obol.MainActivity
import app.obol.R
import app.obol.data.Patron
import app.obol.data.Presets
import app.obol.domain.Blessing
import app.obol.ui.components.Fmt

/** The oracle speaks once a day, when a patron's double hours begin. */
object Oracles {
    private const val CHANNEL = "oracle"
    private const val ID = 9

    /** Returns whether the oracle could speak (notifications may be off). */
    fun tell(context: Context, patron: Patron, b: Blessing): Boolean {
        if (!Notices.canPost(context)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_oracle), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val name = Presets.patronName(context, patron)
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_obol)
            .setContentTitle(context.getString(R.string.oracle_title))
            .setContentText(context.getString(R.string.oracle_text, name, Fmt.hm(b.to)))
            .setColor(0xFFD9774A.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching { nm.notify(ID, n) }.isSuccess
    }
}
