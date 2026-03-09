# Android Integration Checklist

Use this checklist when integrating and testing `capacitor-plugin-geofence` on Android.

## Host App Setup

- [ ] `@capacitor/android` is installed in the host app
- [ ] Android project exists (`npx cap add android`)
- [ ] Plugin is synced (`npx cap sync android`)
- [ ] Device/emulator has Google Play Services
- [ ] Location service is turned on in Android settings

## Permissions

- [ ] Request foreground location (`requestLocationPermission`)
- [ ] Request background location (`requestBackgroundLocationPermission`)
- [ ] Request notifications on Android 13+ (`requestNotificationPermission`)
- [ ] Confirm `checkPermissionStatus` returns expected states

## Geofence Registration Flow

- [ ] Call `initialize()`
- [ ] Call `addOrUpdate(...)` with at least one valid geofence
- [ ] Verify `getWatched()` contains the geofence
- [ ] Verify notifications are configured in the payload

## Runtime Test

- [ ] Move into/out of the geofence (or use mock location route)
- [ ] Confirm `transitionReceived` listener fires
- [ ] Confirm local notification appears
- [ ] Tap notification and confirm `notificationClicked` listener fires

## Background / Killed App Test

- [ ] Put app in background and cross boundary
- [ ] Force-kill app and cross boundary again
- [ ] Verify transition still triggers OS notification
- [ ] Tap notification and verify app opens with callback payload

## Reboot Persistence Test

- [ ] Add geofence
- [ ] Reboot device/emulator
- [ ] Confirm geofence is restored automatically
- [ ] Cross boundary and verify notification/callback flow

## Cleanup

- [ ] `remove(...)` removes specific geofence
- [ ] `removeAll()` clears all geofences
- [ ] `dismissNotifications(...)` removes delivered notifications
