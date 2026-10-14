package com.krstechsolutions.krslocation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.krstechsolutions.krslocation.LocationPrefs
import com.krstechsolutions.krslocation.MainActivity
import com.krstechsolutions.krslocation.MockLocationService
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.model.LatLng

/** Home screen widget showing the mock status with a start / stop button. */
class MockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            if (MockLocationService.isRunning) {
                MockLocationService.stop(context)
            } else {
                // Tapping a widget grants a short foreground-start allowance.
                MockLocationService.startAtLastPosition(context)
            }
            updateAll(context)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.krstechsolutions.krslocation.widget.TOGGLE"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, MockWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val state = MockLocationService.state.value
            val running = state?.running == true
            val prefs = LocationPrefs(context)
            val coords = if (running && state != null) {
                LatLng(state.latitude, state.longitude).format(5)
            } else {
                LatLng(prefs.latitude, prefs.longitude).format(5)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_mock)
            views.setTextViewText(
                R.id.widget_status,
                context.getString(
                    when {
                        running && state?.route != null -> R.string.widget_status_route
                        running && state?.moving == true -> R.string.widget_status_walking
                        running -> R.string.widget_status_on
                        else -> R.string.widget_status_off
                    }
                )
            )
            views.setTextViewText(R.id.widget_coords, coords)
            views.setTextViewText(
                R.id.widget_toggle,
                context.getString(if (running) R.string.action_stop else R.string.action_start_short)
            )
            views.setImageViewResource(
                R.id.widget_dot,
                if (running) R.drawable.bg_widget_dot_on else R.drawable.bg_widget_dot_off
            )

            val toggle = PendingIntent.getBroadcast(
                context, 10,
                Intent(context, MockWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, toggle)

            val open = PendingIntent.getActivity(
                context, 11,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
