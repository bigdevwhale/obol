package app.obol.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.obol.MainActivity
import app.obol.R

/** Charon's notices: the quiet one that keeps him in the foreground. */
object Notices {
    private const val WATCH_CHANNEL = "watch"
    const val WATCH_ID = 5

    /** The quiet, permanent notice that keeps Charon in the foreground so the system doesn't freeze him. */
    fun watch(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(WATCH_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(WATCH_CHANNEL, context.getString(R.string.channel_watch), NotificationManager.IMPORTANCE_MIN).apply {
                    description = context.getString(R.string.channel_watch_desc)
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, WATCH_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_obol)
            .setContentTitle(context.getString(R.string.watch_title))
            .setContentText(context.getString(R.string.watch_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setColor(0xFFD9774A.toInt())
            .build()
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
