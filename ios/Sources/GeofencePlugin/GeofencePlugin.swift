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

    private func getArray<T>(_ call: CAPPluginCall, key: String) -> [T]? {
        return call.options[key] as? [T]
    }

    private func getString(_ call: CAPPluginCall, key: String) -> String? {
        return call.options[key] as? String
    }

    private func getInt(_ call: CAPPluginCall, key: String) -> Int? {
        return call.options[key] as? Int
    }


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
        guard let geofences: [JSObject] = getArray(call, key: "geofences") else {
            call.resolve()
            return
        }
        do {
            try engine.addOrUpdate(geofences: geofences)
            call.resolve()
        } catch {
            call.resolve([
                "error": "ADD_GEOFENCE_FAILED",
                "message": "\(error)"
            ])
        }
    }

    @objc func remove(_ call: CAPPluginCall) {
        guard let ids: [String] = getArray(call, key: "ids") else {
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
        let ids: [Int] = getArray(call, key: "ids") ?? []
        engine.dismissNotifications(ids: ids)
        call.resolve()
    }

    @objc func snooze(_ call: CAPPluginCall) {
        if let id = getString(call, key: "id"), let duration = getInt(call, key: "duration") {
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
