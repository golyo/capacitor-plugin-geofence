# iOS Integration Checklist

Use this checklist when integrating and testing `capacitor-plugin-geofence` on iOS.

## Host App Setup

- [ ] `@capacitor/ios` is installed in the host app
- [ ] iOS project exists (`npx cap add ios`)
- [ ] Plugin is synced (`npx cap sync ios`)
- [ ] Physical iPhone testing is preferred for reliable background behavior

## Required Info.plist Entries

- [ ] `NSLocationWhenInUseUsageDescription`
- [ ] `NSLocationAlwaysAndWhenInUseUsageDescription`
- [ ] (Optional fallback) `NSLocationAlwaysUsageDescription`
- [ ] `NSUserNotificationUsageDescription` (if used by your app policy)

## Required Xcode Capabilities

- [ ] Background Modes enabled
- [ ] `Location updates` enabled under Background Modes
- [ ] Push Notifications capability is optional (local notifications do not require APNs)

## Permissions

- [ ] Request foreground location (`requestLocationPermission`)
- [ ] Request background location (`requestBackgroundLocationPermission`)
- [ ] Request notifications (`requestNotificationPermission`)
- [ ] Confirm `checkPermissionStatus` returns expected states

## Geofence Registration Flow

- [ ] Call `initialize()`
- [ ] Call `addOrUpdate(...)` with valid geofence payload
- [ ] Verify `getWatched()` returns the saved geofences

## Runtime Test

- [ ] Cross geofence boundary (or use GPX/mock route)
- [ ] Confirm `transitionReceived` listener fires
- [ ] Confirm local notification appears
- [ ] Tap notification and confirm `notificationClicked` listener fires

## Background / Killed App Test

- [ ] Put app in background and cross boundary
- [ ] Terminate app and cross boundary again
- [ ] Verify notification is still shown by iOS
- [ ] Tap notification and verify callback payload is delivered on open

## Reboot / Relaunch Persistence Test

- [ ] Add geofence
- [ ] Restart app/device
- [ ] Confirm monitored geofences are restored
- [ ] Re-test entry/exit transitions

## Cleanup

- [ ] `remove(...)` removes specific geofence
- [ ] `removeAll()` clears all registered geofences
- [ ] `dismissNotifications(...)` clears delivered notifications
