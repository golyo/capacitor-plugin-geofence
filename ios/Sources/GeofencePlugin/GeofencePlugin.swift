import Foundation
import Capacitor

@objc(GeofencePlugin)
public class GeofencePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "GeofencePlugin"
    public let jsName = "Geofence"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "checkPermissionStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestLocationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestBackgroundLocationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestNotificationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addOrUpdate", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "remove", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeAll", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getWatched", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "dismissNotifications", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "snooze", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deviceReady", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "ping", returnType: CAPPluginReturnPromise)
    ]
    private let engine = GeofenceEngine()

    public override func load() {
        super.load()

        engine.onTransitionReceived = { [weak self] geofences in
            self?.notifyListeners("transitionReceived", data: [
                "geofences": geofences
            ], retainUntilConsumed: true)
        }

        engine.onNotificationClicked = { [weak self] data in
            self?.notifyListeners("notificationClicked", data: [
                "data": data
            ], retainUntilConsumed: true)
        }
    }

    @objc func checkPermissionStatus(_ call: CAPPluginCall) {
        engine.checkPermissionStatus { payload in
            call.resolve(payload)
        }
    }

    @objc func requestLocationPermission(_ call: CAPPluginCall) {
        engine.requestLocationPermission { payload in
            call.resolve(payload)
        }
    }

    @objc func requestBackgroundLocationPermission(_ call: CAPPluginCall) {
        engine.requestBackgroundLocationPermission { payload in
            call.resolve(payload)
        }
    }

    @objc func requestNotificationPermission(_ call: CAPPluginCall) {
        engine.requestNotificationPermission { payload in
            call.resolve(payload)
        }
    }

    @objc func initialize(_ call: CAPPluginCall) {
        engine.initialize { result in
            call.resolve(result)
        }
    }

    @objc func addOrUpdate(_ call: CAPPluginCall) {
        guard let geofences = call.getArray("geofences", JSObject.self) else {
            call.resolve()
            return
        }
        do {
            try engine.addOrUpdate(geofences: geofences)
            call.resolve()
        } catch {
            call.reject("ADD_GEOFENCE_FAILED", "\(error)", error)
        }
    }

    @objc func remove(_ call: CAPPluginCall) {
        guard let ids = call.getArray("ids", String.self) else {
            call.resolve()
            return
        }
        engine.remove(ids: ids)
        call.resolve()
    }

    @objc func removeAll(_ call: CAPPluginCall) {
        engine.removeAll()
        call.resolve()
    }

    @objc func getWatched(_ call: CAPPluginCall) {
        let geofences = engine.getWatched()
        call.resolve([
            "geofences": geofences
        ])
    }

    @objc func dismissNotifications(_ call: CAPPluginCall) {
        let ids = call.getArray("ids", Int.self) ?? []
        engine.dismissNotifications(ids: ids)
        call.resolve()
    }

    @objc func snooze(_ call: CAPPluginCall) {
        if let id = call.getString("id"), let duration = call.getInt("duration") {
            engine.snooze(id: id, durationSeconds: duration)
        }
        call.resolve()
    }

    @objc func deviceReady(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func ping(_ call: CAPPluginCall) {
        call.resolve()
    }

}
