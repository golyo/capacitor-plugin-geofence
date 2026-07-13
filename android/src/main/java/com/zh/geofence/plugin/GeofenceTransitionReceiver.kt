package com.zh.geofence.plugin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transitionType = event.geofenceTransition
        if (
            transitionType != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transitionType != Geofence.GEOFENCE_TRANSITION_EXIT &&
            transitionType != Geofence.GEOFENCE_TRANSITION_DWELL
        ) {
            return
        }

        val storage = GeofenceStorage(context)
        val transitioned = org.json.JSONArray()

        val triggeringGeofences = event.triggeringGeofences ?: emptyList()
        for (geofence in triggeringGeofences) {
            val id = geofence.requestId
            val stored = storage.getById(id) ?: continue

            if (storage.isSnoozed(id)) continue
            if (!isWithinTimeRange(stored)) continue
            if (!acceptsTransition(stored.optInt("transitionType", 3), transitionType)) continue

            val payload = JSONObject(stored.toString())
            payload.put("transitionType", transitionType)
            transitioned.put(payload)
            sendNotificationIfConfigured(context, payload, transitionType)
        }

        if (transitioned.length() > 0) {
            GeofencePlugin.notifyTransitionReceived(transitioned)
        }
    }

    private fun acceptsTransition(configTransitionType: Int, actualTransitionType: Int): Boolean {
        return when (configTransitionType) {
            1 -> actualTransitionType == Geofence.GEOFENCE_TRANSITION_ENTER
            2 -> actualTransitionType == Geofence.GEOFENCE_TRANSITION_EXIT
            3 -> actualTransitionType == Geofence.GEOFENCE_TRANSITION_ENTER || actualTransitionType == Geofence.GEOFENCE_TRANSITION_EXIT
            else -> true
        }
    }

    private fun isWithinTimeRange(geofence: JSONObject): Boolean {
        val start = geofence.optString("startTime", "")
        val end = geofence.optString("endTime", "")
        if (start.isEmpty() && end.isEmpty()) return true

        val now = Date()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        if (start.isNotEmpty()) {
            val startDate = runCatching { isoFormat.parse(start) }.getOrNull()
            if (startDate != null && now.before(startDate)) return false
        }
        if (end.isNotEmpty()) {
            val endDate = runCatching { isoFormat.parse(end) }.getOrNull()
            if (endDate != null && now.after(endDate)) return false
        }
        return true
    }

    private fun sendNotificationIfConfigured(context: Context, geofence: JSONObject, transitionType: Int) {
        val notification = geofence.optJSONObject("notification") ?: return

        if (Build.VERSION.SDK_INT >= 33) {
            val hasPermission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                return
            }
        }

        ensureChannel(context)
        val id = notification.optInt("id", geofence.optString("id").hashCode())
        val transition = when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
            else -> "dwell"
        }
        val title = notification.optString("title", "Geofence").replace("\$transition", transition)
        val text = notification.optString("text", "Geofence transition received")
        val data = notification.opt("data")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (notification.optBoolean("openAppOnClick", false)) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                val payload = if (data == null) geofence.toString() else data.toString()
                launchIntent.putExtra(GeofencePlugin.EXTRA_NOTIFICATION_DATA, payload)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                
                val flags = if (Build.VERSION.SDK_INT >= 31) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    id,
                    launchIntent,
                    flags
                )
                builder.setContentIntent(pendingIntent)
            }
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Geofence",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "geofence_transitions"
    }
}
