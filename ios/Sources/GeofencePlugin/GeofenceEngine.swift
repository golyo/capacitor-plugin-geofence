import Foundation
import CoreLocation
import UserNotifications
import Capacitor

final class GeofenceEngine: NSObject, CLLocationManagerDelegate, UNUserNotificationCenterDelegate {
    private let locationManager = CLLocationManager()
    private let notificationCenter = UNUserNotificationCenter.current()
    private let store = UserDefaults.standard
    private let watchedStoreKey = "geofence.watched"
    private let snoozedStoreKey = "geofence.snoozedUntil"

    private var watched: [String: JSObject] = [:]
    private var snoozedFences: [String: TimeInterval] = [:]
    private var initCompletion: ((JSObject) -> Void)?
    private var initRequested: [String] = []
    private var pendingLocationPermissionCompletion: ((JSObject) -> Void)?
    private var pendingBackgroundPermissionCompletion: ((JSObject) -> Void)?
    private var pendingBackgroundAlwaysRequested = false

    var onTransitionReceived: (([JSObject]) -> Void)?
    var onNotificationClicked: ((Any) -> Void)?

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        notificationCenter.delegate = self
        watched = loadWatched()
        snoozedFences = loadSnoozed()
        restoreMonitoring()
    }

    func initialize(completion: @escaping (JSObject) -> Void) {
        initCompletion = completion
        initRequested = []
        continueInitializeFlow()
    }

    func checkPermissionStatus(completion: @escaping (JSObject) -> Void) {
        notificationCenter.getNotificationSettings { settings in
            completion(self.buildPermissionStatusPayload(notificationSettings: settings))
        }
    }

    func requestLocationPermission(completion: @escaping (JSObject) -> Void) {
        if CLLocationManager.authorizationStatus() == .authorizedAlways ||
            CLLocationManager.authorizationStatus() == .authorizedWhenInUse {
            checkPermissionStatus(completion: completion)
            return
        }
        pendingLocationPermissionCompletion = completion
        locationManager.requestWhenInUseAuthorization()
    }

    func requestBackgroundLocationPermission(completion: @escaping (JSObject) -> Void) {
        let status = CLLocationManager.authorizationStatus()
        if status == .authorizedAlways {
            checkPermissionStatus(completion: completion)
            return
        }
        pendingBackgroundPermissionCompletion = completion
        pendingBackgroundAlwaysRequested = false
        if status == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        } else {
            pendingBackgroundAlwaysRequested = true
            locationManager.requestAlwaysAuthorization()
        }
    }

    func requestNotificationPermission(completion: @escaping (JSObject) -> Void) {
        notificationCenter.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in
            self.checkPermissionStatus(completion: completion)
        }
    }

    func addOrUpdate(geofences: [JSObject]) throws {
        for geofence in geofences {
            guard let id = geofence["id"] as? String, !id.isEmpty else { continue }
            watched[id] = geofence
            try startMonitoring(geofence: geofence)
        }
        persistWatched()
    }

    func remove(ids: [String]) {
        for id in ids {
            watched.removeValue(forKey: id)
            snoozedFences.removeValue(forKey: id)
            if let region = monitoredRegion(id: id) {
                locationManager.stopMonitoring(for: region)
            }
        }
        persistWatched()
        persistSnoozed()
    }

    func removeAll() {
        watched.removeAll()
        snoozedFences.removeAll()
        for region in locationManager.monitoredRegions {
            locationManager.stopMonitoring(for: region)
        }
        persistWatched()
        persistSnoozed()
    }

    func getWatched() -> [JSObject] {
        return watched.values.map { $0 }
    }

    func dismissNotifications(ids: [Int]) {
        let idStrings = ids.map { String($0) }
        notificationCenter.removeDeliveredNotifications(withIdentifiers: idStrings)
        notificationCenter.removePendingNotificationRequests(withIdentifiers: idStrings)
    }

    func snooze(id: String, durationSeconds: Int) {
        snoozedFences[id] = Date().timeIntervalSince1970 + TimeInterval(durationSeconds)
        persistSnoozed()
    }

    private func startMonitoring(geofence: JSObject) throws {
        guard
            let id = geofence["id"] as? String,
            let latitude = numberValue(from: geofence["latitude"]),
            let longitude = numberValue(from: geofence["longitude"]),
            let radius = numberValue(from: geofence["radius"])
        else {
            throw NSError(domain: "GeofenceEngine", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid geofence payload"])
        }

        if let existing = monitoredRegion(id: id) {
            locationManager.stopMonitoring(for: existing)
        }

        let region = CLCircularRegion(center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), radius: radius, identifier: id)
        let transitionType = intValue(from: geofence["transitionType"]) ?? 1
        region.notifyOnEntry = transitionType == 1 || transitionType == 3
        region.notifyOnExit = transitionType == 2 || transitionType == 3
        locationManager.startMonitoring(for: region)
    }

    private func monitoredRegion(id: String) -> CLRegion? {
        return locationManager.monitoredRegions.first { $0.identifier == id }
    }

    private func restoreMonitoring() {
        for geofence in watched.values {
            try? startMonitoring(geofence: geofence)
        }
    }

    private func handleTransition(id: String, transitionType: Int) {
        guard var geofence = watched[id] else { return }
        if isSnoozed(id: id) { return }
        if !isWithinTimeRange(geofence: geofence) { return }

        geofence["transitionType"] = transitionType
        watched[id] = geofence
        persistWatched()

        sendNotificationIfConfigured(geofence: geofence, transitionType: transitionType)
        onTransitionReceived?([geofence])
    }

    private func isSnoozed(id: String) -> Bool {
        guard let until = snoozedFences[id] else { return false }
        let now = Date().timeIntervalSince1970
        if now >= until {
            snoozedFences.removeValue(forKey: id)
            persistSnoozed()
            return false
        }
        return true
    }

    private func isWithinTimeRange(geofence: JSObject) -> Bool {
        let now = Date()
        if let start = geofence["startTime"] as? String, let startDate = parseISODate(start), now < startDate {
            return false
        }
        if let end = geofence["endTime"] as? String, let endDate = parseISODate(end), now > endDate {
            return false
        }
        return true
    }

    private func sendNotificationIfConfigured(geofence: JSObject, transitionType: Int) {
        guard var notification = geofence["notification"] as? JSObject else { return }

        let frequency = intValue(from: notification["frequency"]) ?? 0
        let lastTriggered = numberValue(from: notification["lastTriggered"]) ?? 0
        let now = Date().timeIntervalSince1970
        if now < lastTriggered + TimeInterval(frequency) {
            return
        }
        notification["lastTriggered"] = now
        if let id = geofence["id"] as? String, var updated = watched[id] {
            updated["notification"] = notification
            watched[id] = updated
            persistWatched()
        }

        let transition = transitionType == 1 ? "enter" : "exit"
        let content = UNMutableNotificationContent()
        let title = (notification["title"] as? String ?? "Geofence").replacingOccurrences(of: "$transition", with: transition)
        content.title = title
        content.body = notification["text"] as? String ?? "Geofence transition received"
        content.sound = .default

        if let data = notification["data"] {
            if JSONSerialization.isValidJSONObject(["d": data]),
               let payload = try? JSONSerialization.data(withJSONObject: data),
               let jsonString = String(data: payload, encoding: .utf8) {
                content.userInfo = ["geofence.notification.data": jsonString]
            } else if let stringData = data as? String {
                content.userInfo = ["geofence.notification.data": stringData]
            }
        }

        let requestId = String(intValue(from: notification["id"]) ?? Int.random(in: 1...Int.max))
        let request = UNNotificationRequest(identifier: requestId, content: content, trigger: nil)
        notificationCenter.add(request)
    }

    private func parseISODate(_ value: String) -> Date? {
        if let date = ISO8601DateFormatter().date(from: value) {
            return date
        }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.date(from: value)
    }

    private func numberValue(from value: Any?) -> Double? {
        if let number = value as? NSNumber { return number.doubleValue }
        if let double = value as? Double { return double }
        if let int = value as? Int { return Double(int) }
        if let string = value as? String { return Double(string) }
        return nil
    }

    private func intValue(from value: Any?) -> Int? {
        if let number = value as? NSNumber { return number.intValue }
        if let int = value as? Int { return int }
        if let double = value as? Double { return Int(double) }
        if let string = value as? String { return Int(string) }
        return nil
    }

    private func persistWatched() {
        if let data = try? JSONSerialization.data(withJSONObject: watched, options: []) {
            store.set(data, forKey: watchedStoreKey)
        }
    }

    private func loadWatched() -> [String: JSObject] {
        guard let data = store.data(forKey: watchedStoreKey),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return [:]
        }
        var result: [String: JSObject] = [:]
        for (key, value) in root {
            if let geofence = value as? JSObject {
                result[key] = geofence
            }
        }
        return result
    }

    private func persistSnoozed() {
        store.set(snoozedFences, forKey: snoozedStoreKey)
    }

    private func loadSnoozed() -> [String: TimeInterval] {
        guard let raw = store.dictionary(forKey: snoozedStoreKey) else {
            return [:]
        }

        var result: [String: TimeInterval] = [:]
        for (key, value) in raw {
            if let number = value as? NSNumber {
                result[key] = number.doubleValue
            } else if let doubleValue = value as? Double {
                result[key] = doubleValue
            } else if let intValue = value as? Int {
                result[key] = TimeInterval(intValue)
            } else if let stringValue = value as? String, let doubleValue = Double(stringValue) {
                result[key] = doubleValue
            }
        }
        return result
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        handleTransition(id: region.identifier, transitionType: 1)
    }

    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        handleTransition(id: region.identifier, transitionType: 2)
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if let completion = pendingLocationPermissionCompletion {
            pendingLocationPermissionCompletion = nil
            checkPermissionStatus(completion: completion)
        }

        if let completion = pendingBackgroundPermissionCompletion {
            let status = CLLocationManager.authorizationStatus()
            if status == .authorizedWhenInUse && !pendingBackgroundAlwaysRequested {
                pendingBackgroundAlwaysRequested = true
                locationManager.requestAlwaysAuthorization()
                return
            }
            if status != .notDetermined {
                pendingBackgroundPermissionCompletion = nil
                pendingBackgroundAlwaysRequested = false
                checkPermissionStatus(completion: completion)
            }
        }

        if initCompletion != nil {
            continueInitializeFlow()
        }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        if let raw = response.notification.request.content.userInfo["geofence.notification.data"] {
            onNotificationClicked?(raw)
        } else {
            onNotificationClicked?(response.notification.request.content.userInfo)
        }
    }

    private func locationPermissionStatusString() -> String {
        switch CLLocationManager.authorizationStatus() {
        case .authorizedAlways, .authorizedWhenInUse:
            return "granted"
        case .notDetermined:
            return "prompt"
        case .denied, .restricted:
            return "denied"
        @unknown default:
            return "prompt"
        }
    }

    private func backgroundPermissionStatusString() -> String {
        switch CLLocationManager.authorizationStatus() {
        case .authorizedAlways:
            return "granted"
        case .notDetermined, .authorizedWhenInUse:
            return "prompt"
        case .denied, .restricted:
            return "denied"
        @unknown default:
            return "prompt"
        }
    }

    private func notificationPermissionStatusString(_ settings: UNNotificationSettings) -> String {
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return "granted"
        case .denied:
            return "denied"
        case .notDetermined:
            return "prompt"
        @unknown default:
            return "prompt"
        }
    }

    private func buildPermissionStatusPayload(notificationSettings: UNNotificationSettings) -> JSObject {
        return [
            "location": locationPermissionStatusString(),
            "backgroundLocation": backgroundPermissionStatusString(),
            "notifications": notificationPermissionStatusString(notificationSettings),
        ]
    }

    private func continueInitializeFlow() {
        checkPermissionStatus { status in
            if self.shouldRequestNotification(status) {
                self.markRequested("notification")
                self.requestNotificationPermission { _ in
                    self.continueInitializeFlow()
                }
                return
            }

            if self.shouldRequestLocation(status) {
                self.markRequested("location")
                self.locationManager.requestWhenInUseAuthorization()
                return
            }

            if self.locationPermissionValue(status) != "granted" {
                self.finalizeInitialize(status)
                return
            }

            if self.shouldRequestBackground(status) {
                self.markRequested("background")
                self.locationManager.requestAlwaysAuthorization()
                return
            }

            self.locationManager.startUpdatingLocation()
            self.locationManager.startMonitoringSignificantLocationChanges()
            self.finalizeInitialize(status)
        }
    }

    private func finalizeInitialize(_ status: JSObject) {
        let missing = computeMissing(status)
        let granted = computeGranted(status)
        let result: JSObject = [
            "ready": missing.isEmpty,
            "missing": missing,
            "requested": initRequested,
            "granted": granted,
        ]
        let completion = initCompletion
        initCompletion = nil
        initRequested = []
        completion?(result)
    }

    private func shouldRequestNotification(_ status: JSObject) -> Bool {
        return notificationPermissionValue(status) != "granted" && !initRequested.contains("notification")
    }

    private func shouldRequestLocation(_ status: JSObject) -> Bool {
        return locationPermissionValue(status) != "granted" && !initRequested.contains("location")
    }

    private func shouldRequestBackground(_ status: JSObject) -> Bool {
        return backgroundPermissionValue(status) != "granted" && !initRequested.contains("background")
    }

    private func markRequested(_ permission: String) {
        if !initRequested.contains(permission) {
            initRequested.append(permission)
        }
    }

    private func computeMissing(_ status: JSObject) -> [String] {
        var missing: [String] = []
        if notificationPermissionValue(status) != "granted" {
            missing.append("notification")
        }
        if locationPermissionValue(status) != "granted" {
            missing.append("location")
            missing.append("background")
            return missing
        }
        if backgroundPermissionValue(status) != "granted" {
            missing.append("background")
        }
        return missing
    }

    private func computeGranted(_ status: JSObject) -> [String] {
        var granted: [String] = []
        if notificationPermissionValue(status) == "granted" {
            granted.append("notification")
        }
        if locationPermissionValue(status) == "granted" {
            granted.append("location")
        }
        if backgroundPermissionValue(status) == "granted" {
            granted.append("background")
        }
        return granted
    }

    private func locationPermissionValue(_ status: JSObject) -> String {
        return (status["location"] as? String) ?? "prompt"
    }

    private func backgroundPermissionValue(_ status: JSObject) -> String {
        return (status["backgroundLocation"] as? String) ?? "prompt"
    }

    private func notificationPermissionValue(_ status: JSObject) -> String {
        return (status["notifications"] as? String) ?? "prompt"
    }
}
