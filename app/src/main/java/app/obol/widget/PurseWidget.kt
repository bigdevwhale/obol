package app.obol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.obol.MainActivity
import app.obol.R
import app.obol.container
import app.obol.domain.Clock
import app.obol.ui.components.Fmt
import kotlinx.coroutines.launch

/** The purse on the home screen: the balance and today's given and taken. */
class PurseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.container.scope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Asks the launcher to place the widget; false when the launcher can't. */
        fun pin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, PurseWidget::class.java), null, null)
        }

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PurseWidget::class.java))
            if (ids.isEmpty()) return
            val dao = context.container.dao
            val day = Clock.day()
            val balance = dao.balance()
            val earned = dao.earnedOn(day)
            val spent = dao.spentOn(day)
            val open = PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_purse).apply {
                setTextViewText(R.id.balance, Fmt.obols(balance))
                setTextViewText(R.id.today, context.getString(R.string.widget_today, Fmt.obols(earned), Fmt.obols(spent)))
                setOnClickPendingIntent(R.id.root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
