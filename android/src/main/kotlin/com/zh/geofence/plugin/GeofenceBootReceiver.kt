package com.zh.geofence.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GeofenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GeofencePlugin.restoreGeofencesFromStorage(context)
        }
    }
}
