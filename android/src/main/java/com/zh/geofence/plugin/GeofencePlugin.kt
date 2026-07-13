package com.zh.geofence.plugin

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PermissionState
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.PermissionCallback
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

@CapacitorPlugin(
    name = "Geofence",
    permissions = [
        Permission(
            alias = "location",
            strings = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION]
        ),
        Permission(
            alias = "backgroundLocation",
            strings = [Manifest.permission.ACCESS_BACKGROUND_LOCATION]
        ),
        Permission(
            alias = "notifications",
            strings = [Manifest.permission.POST_NOTIFICATIONS]
        )
    ]
)
class GeofencePlugin : Plugin() {
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var storage: GeofenceStorage
    private val initStates = mutableMapOf<String, InitState>()

    override fun load() {
        super.load()
        geofencingClient = LocationServices.getGeofencingClient(context)
        storage = GeofenceStorage(context)
        setPluginInstance(this)
        restoreGeofencesFromStorage(context)
    }

    @PluginMethod
    fun checkPermissionStatus(call: PluginCall) {
        call.resolve(buildPermissionStatePayload())
    }

    @PluginMethod
    fun requestLocationPermission(call: PluginCall) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            call.resolve(buildPermissionStatePayload())
            return
        }
        requestPermissionForAlias("location", call, "permissionRequestCallback")
    }

    @PluginMethod
    fun requestBackgroundLocationPermission(call: PluginCall) {
        if (android.os.Build.VERSION.SDK_INT <= 28) {
            call.resolve(buildPermissionStatePayload())
            return
        }
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "requestBackgroundAfterLocationPermissionCallback")
            return
        }
        if (getPermissionState("backgroundLocation") == PermissionState.GRANTED) {
            call.resolve(buildPermissionStatePayload())
            return
        }
        requestPermissionForAlias("backgroundLocation", call, "permissionRequestCallback")
    }

    @PermissionCallback
    private fun requestBackgroundAfterLocationPermissionCallback(call: PluginCall) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            call.reject("LOCATION_PERMISSION_REQUIRED_TO_REQUEST_BACKGROUND")
            return
        }
        if (android.os.Build.VERSION.SDK_INT <= 28 || getPermissionState("backgroundLocation") == PermissionState.GRANTED) {
            call.resolve(buildPermissionStatePayload())
            return
        }
        requestPermissionForAlias("backgroundLocation", call, "permissionRequestCallback")
    }

    @PluginMethod
    fun requestNotificationPermission(call: PluginCall) {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            call.resolve(buildPermissionStatePayload())
            return
        }
        if (getPermissionState("notifications") == PermissionState.GRANTED) {
            call.resolve(buildPermissionStatePayload())
            return
        }
        requestPermissionForAlias("notifications", call, "permissionRequestCallback")
    }

    @PermissionCallback
    private fun permissionRequestCallback(call: PluginCall) {
        call.resolve(buildPermissionStatePayload())
    }

    @PluginMethod
    fun initialize(call: PluginCall) {
        val state = InitState()
        initStates[call.callbackId] = state
        continueInitializeFlow(call)
    }

    @PermissionCallback
    private fun initializeNotificationPermissionCallback(call: PluginCall) {
        continueInitializeFlow(call)
    }

    @PermissionCallback
    private fun initializeLocationPermissionCallback(call: PluginCall) {
        continueInitializeFlow(call)
    }

    @PermissionCallback
    private fun initializeBackgroundPermissionCallback(call: PluginCall) {
        continueInitializeFlow(call)
    }

    @PluginMethod
    fun addOrUpdate(call: PluginCall) {
        val geofences = call.getArray("geofences") ?: JSONArray()
        if (geofences.length() == 0) {
            call.resolve()
            return
        }

        val geofenceList = mutableListOf<Geofence>()
        val idsToReplace = mutableListOf<String>()
        for (index in 0 until geofences.length()) {
            val geofence = geofences.optJSONObject(index) ?: continue
            val id = geofence.optString("id")
            if (id.isEmpty()) continue
            idsToReplace.add(id)
            geofenceList.add(toNativeGeofence(geofence))
        }
        if (geofenceList.isEmpty()) {
            call.reject("INVALID_GEOFENCE", "No valid geofence payload provided")
            return
        }

        geofencingClient
            .removeGeofences(idsToReplace)
            .addOnCompleteListener {
                geofencingClient
                    .addGeofences(createGeofencingRequest(geofenceList), getGeofencePendingIntent(context))
                    .addOnSuccessListener {
                        for (index in 0 until geofences.length()) {
                            val geofence = geofences.optJSONObject(index) ?: continue
                            storage.upsert(geofence)
                        }
                        call.resolve()
                    }
                    .addOnFailureListener { exception ->
                        call.reject("ADD_GEOFENCE_FAILED", exception.message, exception)
                    }
            }
    }

    @PluginMethod
    fun remove(call: PluginCall) {
        val ids = call.getArray("ids") ?: JSONArray()
        if (ids.length() == 0) {
            call.resolve()
            return
        }
        val idList = mutableListOf<String>()
        for (index in 0 until ids.length()) {
            val id = ids.optString(index)
            if (id.isNotEmpty()) {
                idList.add(id)
            }
        }
        if (idList.isEmpty()) {
            call.resolve()
            return
        }
        geofencingClient.removeGeofences(idList)
            .addOnSuccessListener {
                storage.remove(idList)
                call.resolve()
            }
            .addOnFailureListener { exception ->
                call.reject("REMOVE_GEOFENCE_FAILED", exception.message, exception)
            }
    }

    @PluginMethod
    fun removeAll(call: PluginCall) {
        val existingIds = storage.getAllIds()
        if (existingIds.isEmpty()) {
            call.resolve()
            return
        }
        geofencingClient.removeGeofences(existingIds)
            .addOnSuccessListener {
                storage.clear()
                call.resolve()
            }
            .addOnFailureListener { exception ->
                call.reject("REMOVE_ALL_GEOFENCE_FAILED", exception.message, exception)
            }
    }

    @PluginMethod
    fun getWatched(call: PluginCall) {
        val geofences = JSONArray()
        for (geo in storage.getAll()) {
            geofences.put(geo)
        }
        val ret = JSObject().apply {
            put("geofences", geofences)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun dismissNotifications(call: PluginCall) {
        val ids = call.getArray("ids") ?: JSONArray()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        for (index in 0 until ids.length()) {
            manager.cancel(ids.optInt(index))
        }
        call.resolve()
    }

    @PluginMethod
    fun snooze(call: PluginCall) {
        val id = call.getString("id")
        val duration = call.getInt("duration")
        if (id != null && duration != null) {
            storage.setSnoozedUntil(id, System.currentTimeMillis() + duration * 1000L)
        }
        call.resolve()
    }

    @PluginMethod
    fun deviceReady(call: PluginCall) {
        call.resolve()
    }

    @PluginMethod
    fun ping(call: PluginCall) {
        call.resolve()
    }

    override fun handleOnNewIntent(intent: Intent) {
        super.handleOnNewIntent(intent)
        val notificationData = intent.getStringExtra(EXTRA_NOTIFICATION_DATA) ?: return
        notifyNotificationClicked(notificationData)
    }

    private fun permissionStateToString(state: PermissionState): String {
        return when (state) {
            PermissionState.GRANTED -> "granted"
            PermissionState.DENIED -> "denied"
            PermissionState.PROMPT -> "prompt"
            PermissionState.PROMPT_WITH_RATIONALE -> "prompt-with-rationale"
        }
    }

    private fun buildPermissionStatePayload(): JSObject {
        val result = JSObject()
        result.put("location", permissionStateToString(getPermissionState("location")))
        result.put("backgroundLocation", permissionStateToString(getPermissionState("backgroundLocation")))
        result.put("notifications", permissionStateToString(getPermissionState("notifications")))
        return result
    }

    private fun toNativeGeofence(geofenceJson: JSONObject): Geofence {
        val id = geofenceJson.optString("id")
        val latitude = geofenceJson.optDouble("latitude")
        val longitude = geofenceJson.optDouble("longitude")
        val radius = geofenceJson.optDouble("radius").toFloat()
        val transitionType = geofenceJson.optInt("transitionType", Geofence.GEOFENCE_TRANSITION_ENTER)
        val loiteringDelay = geofenceJson.optInt("loiteringDelay", 60 * 60 * 1000)

        return Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(latitude, longitude, radius)
            .setTransitionTypes(
                when (transitionType) {
                    1 -> Geofence.GEOFENCE_TRANSITION_ENTER
                    2 -> Geofence.GEOFENCE_TRANSITION_EXIT
                    3 -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                    else -> transitionType
                }
            )
            .setLoiteringDelay(loiteringDelay)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
    }

    companion object {
        private const val TAG = "GeofencePlugin"
        const val EXTRA_NOTIFICATION_DATA = "geofence.notification.data"
        private var pluginRef: WeakReference<GeofencePlugin>? = null

        private fun setPluginInstance(plugin: GeofencePlugin) {
            pluginRef = WeakReference(plugin)
        }

        fun notifyTransitionReceived(geofences: JSONArray) {
            val plugin = pluginRef?.get()
            if (plugin != null) {
                val data = JSObject()
                data.put("geofences", geofences)
                plugin.notifyListeners("transitionReceived", data, true)
            } else {
                Log.i(TAG, "Plugin instance unavailable, transition event queued by OS only")
            }
        }

        fun notifyNotificationClicked(notificationData: String) {
            val plugin = pluginRef?.get() ?: return
            val data = JSObject()
            data.put("data", parseJsonOrRaw(notificationData))
            plugin.notifyListeners("notificationClicked", data, true)
        }

        private fun parseJsonOrRaw(value: String): Any {
            return try {
                JSONObject(value)
            } catch (_: Exception) {
                value
            }
        }

        fun createGeofencingRequest(geofences: List<Geofence>): GeofencingRequest {
            return GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()
        }

        fun getGeofencePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, GeofenceTransitionReceiver::class.java)
            // Geofencing API populates transition data on the PendingIntent,
            // so this must remain mutable on Android 12+.
            val flags = if (android.os.Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, 1001, intent, flags)
        }

        fun restoreGeofencesFromStorage(context: Context) {
            val storage = GeofenceStorage(context)
            val all = storage.getAll()
            if (all.isEmpty()) {
                return
            }
            val geofences = all.mapNotNull {
                try {
                    Geofence.Builder()
                        .setRequestId(it.optString("id"))
                        .setCircularRegion(
                            it.optDouble("latitude"),
                            it.optDouble("longitude"),
                            it.optDouble("radius").toFloat()
                        )
                        .setTransitionTypes(
                            when (it.optInt("transitionType", 1)) {
                                1 -> Geofence.GEOFENCE_TRANSITION_ENTER
                                2 -> Geofence.GEOFENCE_TRANSITION_EXIT
                                3 -> Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                                else -> Geofence.GEOFENCE_TRANSITION_ENTER
                            }
                        )
                        .setLoiteringDelay(it.optInt("loiteringDelay", 60 * 60 * 1000))
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .build()
                } catch (_: Exception) {
                    null
                }
            }
            if (geofences.isEmpty()) return

            val geofencingClient = LocationServices.getGeofencingClient(context)
            geofencingClient.addGeofences(createGeofencingRequest(geofences), getGeofencePendingIntent(context))
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to restore geofences from storage", exception)
                }
        }
    }

    private data class InitState(
        val requested: MutableSet<String> = mutableSetOf()
    )

    private fun continueInitializeFlow(call: PluginCall) {
        val state = initStates[call.callbackId] ?: InitState()
        initStates[call.callbackId] = state

        if (shouldRequestNotification() && !state.requested.contains("notification")) {
            state.requested.add("notification")
            requestPermissionForAlias("notifications", call, "initializeNotificationPermissionCallback")
            return
        }

        if (getPermissionState("location") != PermissionState.GRANTED && !state.requested.contains("location")) {
            state.requested.add("location")
            requestPermissionForAlias("location", call, "initializeLocationPermissionCallback")
            return
        }

        if (getPermissionState("location") == PermissionState.GRANTED &&
            shouldRequestBackground() &&
            !state.requested.contains("background")
        ) {
            state.requested.add("background")
            requestPermissionForAlias("backgroundLocation", call, "initializeBackgroundPermissionCallback")
            return
        }

        finalizeInitialize(call)
    }

    private fun finalizeInitialize(call: PluginCall) {
        val state = initStates.remove(call.callbackId) ?: InitState()

        val missing = mutableListOf<String>()
        val granted = mutableListOf<String>()

        if (isNotificationPermissionRelevant()) {
            if (getPermissionState("notifications") == PermissionState.GRANTED) {
                granted.add("notification")
            } else {
                missing.add("notification")
            }
        }

        if (getPermissionState("location") == PermissionState.GRANTED) {
            granted.add("location")
            if (isBackgroundPermissionRelevant()) {
                if (getPermissionState("backgroundLocation") == PermissionState.GRANTED) {
                    granted.add("background")
                } else {
                    missing.add("background")
                }
            } else {
                granted.add("background")
            }
        } else {
            missing.add("location")
            missing.add("background")
        }

        val result = JSObject()
        result.put("ready", missing.isEmpty())
        result.put("missing", JSONArray(missing))
        result.put("requested", JSONArray(state.requested.toList()))
        result.put("granted", JSONArray(granted))
        call.resolve(result)
    }

    private fun isBackgroundPermissionRelevant(): Boolean = android.os.Build.VERSION.SDK_INT > 28

    private fun isNotificationPermissionRelevant(): Boolean = android.os.Build.VERSION.SDK_INT >= 33

    private fun shouldRequestBackground(): Boolean {
        return isBackgroundPermissionRelevant() && getPermissionState("backgroundLocation") != PermissionState.GRANTED
    }

    private fun shouldRequestNotification(): Boolean {
        return isNotificationPermissionRelevant() && getPermissionState("notifications") != PermissionState.GRANTED
    }
}
