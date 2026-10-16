package app.obol.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import app.obol.MainActivity
import app.obol.R
import app.obol.data.Crossing
import app.obol.ui.components.Fmt
import org.json.JSONObject

/**
 * The crossing as a live notification: a countdown that HyperOS lifts into its Super Island
 * (Xiaomi's focus notification protocol) and Android 16 shows as a Live Update chip. The
 * camera cut-out never hides it, unlike a badge drawn at the top of the screen.
 */
object Island {
    private const val CHANNEL = "crossing"
    private const val ID = 7

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_crossing), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_crossing_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** HyperOS focus protocol version: 3 and up has the island. 0 elsewhere. */
    fun focusProtocol(context: Context): Int =
        runCatching { Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) }.getOrDefault(0)

    /** Whether HyperOS lets this app show focus notifications at all. */
    fun focusAllowed(context: Context): Boolean = runCatching {
        val b = context.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"),
            "canShowFocus",
            null,
            Bundle().apply { putString("package", context.packageName) },
        )
        b?.getBoolean("canShowFocus", false) ?: false
    }.getOrDefault(false)

    /** Shows or updates the crossing. Cheap to call every few seconds: the system counts down by itself. */
    fun show(context: Context, stream: String, c: Crossing, balance: Long) {
        if (!canPost(context)) return
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val end = now + c.leftSec * 1000L
        val title = stream
        val text = if (c.meter) context.getString(R.string.island_meter, Fmt.rate(c.perMin), Fmt.obols(balance))
        else context.getString(R.string.island_left, Fmt.clock(c.leftSec))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_obol)

        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(if (c.meter) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STOPWATCH)
            .setColor(0xFFD9774A.toInt())
            .setUsesChronometer(true)
            .setShowWhen(true)
        if (c.meter) {
            b.setWhen(now - c.usedSec * 1000L)
        } else {
            b.setWhen(end).setChronometerCountDown(true)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 Live Update: a chip in the status bar with the time left.
            b.setShortCriticalText(if (c.meter) "−${Fmt.rate(c.perMin)}" else Fmt.clock(c.leftSec))
            // Builder.setRequestPromotedOngoing is newer than compileSdk 36; this is the extra it sets.
            b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        }

        // Xiaomi focus notification: the Super Island with the boat on the left and the timer on the right.
        val pics = Bundle().apply { putParcelable(PIC, Icon.createWithResource(context, R.drawable.ic_island_boat)) }
        val timer = JSONObject().apply {
            put("timerType", if (c.meter) 1 else -1)
            put("timerWhen", if (c.meter) now - c.usedSec * 1000L else end)
            put("timerSystemCurrent", now)
            if (!c.meter) put("timerTotal", (c.leftSec + c.usedSec) * 1000L)
        }
        val param = JSONObject().put(
            "param_v2",
            JSONObject().apply {
                put("protocol", 1)
                put("business", "obol_crossing")
                put("updatable", true)
                put("enableFloat", false)
                put("islandFirstFloat", false)
                put("ticker", "$title · $text")
                put("tickerPic", PIC)
                put("aodTitle", text)
                put("aodPic", PIC)
                put("baseInfo", JSONObject().put("type", 2).put("title", title).put("content", text))
                put(
                    "highlightInfo",
                    JSONObject().put("type", 1).put("title", title).put("content", text).put("picFunction", PIC).put("timerInfo", timer),
                )
                put(
                    "param_island",
                    JSONObject().apply {
                        put("islandProperty", 1)
                        put(
                            "bigIslandArea",
                            JSONObject().apply {
                                put(
                                    "imageTextInfoLeft",
                                    JSONObject().put("type", 1)
                                        .put("picInfo", JSONObject().put("type", 1).put("pic", PIC))
                                        .put("textInfo", JSONObject().put("title", title)),
                                )
                                put("sameWidthDigitInfo", JSONObject().put("timerInfo", timer))
                            },
                        )
                        put("smallIslandArea", JSONObject().put("picInfo", JSONObject().put("type", 1).put("pic", PIC)))
                    },
                )
            },
        )
        b.addExtras(Bundle().apply {
            putString("miui.focus.param", param.toString())
            putBundle("miui.focus.pics", pics)
        })
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, b.build()) }
            .onFailure { android.util.Log.w("Charon", "island", it) }
    }

    /** The island will carry the countdown, so the plaque over apps is not needed. */
    fun works(context: Context): Boolean = canPost(context) && focusProtocol(context) >= 3 && focusAllowed(context)

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private const val PIC = "miui.focus.pic_boat"
}
