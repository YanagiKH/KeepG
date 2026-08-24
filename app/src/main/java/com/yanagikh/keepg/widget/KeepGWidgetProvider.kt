package com.yanagikh.keepg.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yanagikh.keepg.MainActivity
import com.yanagikh.keepg.R

class KeepGWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.keepg_widget).apply {
                setOnClickPendingIntent(R.id.widget_root, destination(context, 10, DEST_PHOTOS))
                setOnClickPendingIntent(R.id.widget_photos, destination(context, 11, DEST_PHOTOS))
                setOnClickPendingIntent(R.id.widget_albums, destination(context, 12, DEST_ALBUMS))
                setOnClickPendingIntent(R.id.widget_camera, destination(context, 13, DEST_CAMERA))
                setOnClickPendingIntent(R.id.widget_vault, destination(context, 14, DEST_VAULT))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun destination(context: Context, requestCode: Int, destination: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_START_DESTINATION, destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_START_DESTINATION = "keepg.start_destination"
        const val DEST_PHOTOS = "PHOTOS"
        const val DEST_ALBUMS = "ALBUMS"
        const val DEST_CAMERA = "CAMERA"
        const val DEST_VAULT = "VAULT"
    }
}
