package com.zh.geofence.plugin

import android.content.Context
import org.json.JSONObject

class GeofenceStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun upsert(geofence: JSONObject) {
        val id = geofence.optString("id")
        if (id.isEmpty()) return
        val all = readAllMap()
        all.put(id, geofence)
        persist(all)
    }

    fun remove(ids: List<String>) {
        if (ids.isEmpty()) return
        val all = readAllMap()
        val snoozed = readSnoozedMap()
        for (id in ids) {
            all.remove(id)
            snoozed.remove(id)
        }
        persist(all)
        persistSnoozed(snoozed)
    }

    fun clear() {
        prefs.edit().remove(KEY_GEOFENCES).apply()
        prefs.edit().remove(KEY_SNOOZED_UNTIL).apply()
    }

    fun getById(id: String): JSONObject? {
        val all = readAllMap()
        if (!all.has(id)) return null
        return all.optJSONObject(id)
    }

    fun getAll(): List<JSONObject> {
        val all = readAllMap()
        return all.keys().asSequence().mapNotNull { key -> all.optJSONObject(key) }.toList()
    }

    fun getAllIds(): List<String> {
        val all = readAllMap()
        return all.keys().asSequence().toList()
    }

    fun setSnoozedUntil(id: String, untilEpochMillis: Long) {
        if (id.isEmpty()) return
        val all = readSnoozedMap()
        all.put(id, untilEpochMillis)
        persistSnoozed(all)
    }

    fun isSnoozed(id: String, nowEpochMillis: Long = System.currentTimeMillis()): Boolean {
        val all = readSnoozedMap()
        val until = all.optLong(id, 0L)
        if (until <= 0L) return false
        if (nowEpochMillis >= until) {
            all.remove(id)
            persistSnoozed(all)
            return false
        }
        return true
    }

    private fun readAllMap(): JSONObject {
        val raw = prefs.getString(KEY_GEOFENCES, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun persist(value: JSONObject) {
        prefs.edit().putString(KEY_GEOFENCES, value.toString()).apply()
    }

    private fun readSnoozedMap(): JSONObject {
        val raw = prefs.getString(KEY_SNOOZED_UNTIL, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun persistSnoozed(value: JSONObject) {
        prefs.edit().putString(KEY_SNOOZED_UNTIL, value.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "capacitor_geofence_storage"
        private const val KEY_GEOFENCES = "geofences"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until"
    }
}
