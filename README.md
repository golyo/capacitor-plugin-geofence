# capacitor-plugin-geofence

geofencing places

## Origin

This Capacitor plugin is based on the legacy `cordova-plugin-geofence` implementation and was migrated to Capacitor-native Android/iOS bridges.

## Migration Notes (Cordova -> Capacitor)

### initialize interface change

- `initialize` now uses a structured permission result model.
- Native Capacitor plugin interface (`GeofencePlugin`) returns:
  - `initialize(): Promise<InitializeResult>`
- Returned `InitializeResult`:
  - `ready: boolean`
  - `missing: ("notification" | "location" | "background")[]`
  - `requested: ("notification" | "location" | "background")[]`
  - `granted: ("notification" | "location" | "background")[]`
- Cordova-compatible JS facade (`Geofence` in `src/index.ts`) keeps callback-style usage and returns `Promise<boolean>`:
  - returns `true` if permissions are already ready
  - returns `false` when async permission flow starts and final details are delivered in callback

### function behavior changes

- `snooze(options: { id, duration })`
  - `duration` is interpreted in seconds
  - suppresses both transition callback (`transitionReceived`) and notification delivery for the selected geofence during the active snooze window
  - snooze state is persisted on Android and iOS and survives app restart until expiration

## Install

To use npm

```bash
npm install capacitor-plugin-geofence
````

To use yarn

```bash
yarn add capacitor-plugin-geofence
```

Sync native files

```bash
npx cap sync
```

## API

## Integration Checklists

- [Android Integration Checklist](./ANDROID_CHECKLIST.md)
- [iOS Integration Checklist](./IOS_CHECKLIST.md)

## Compatibility

### iOS

- Minimum supported iOS version: `15.0`
- Configured in both:
  - `Package.swift` (`platforms: [.iOS(.v15)]`)
  - `CapacitorPluginGeofence.podspec` (`s.ios.deployment_target = '15.0'`)
- Expected to work on current iOS major versions (15+), including recent releases.

### Android

- `minSdkVersion`: `24`
- `targetSdkVersion`: `35`
- `compileSdk`: `35`
- Designed to be compatible with Android API `33+` permission model:
  - `POST_NOTIFICATIONS` runtime permission handling
  - foreground/background location permission separation
  - geofencing `PendingIntent` kept mutable where required by Play Services geofencing delivery

## Behavior Notes

### initialize

- `initialize(callback)` is permission-focused (does not add/remove geofences)
- Android and iOS use the same flow:
  1. notification permission (if missing)
  2. foreground location permission (if missing)
  3. if foreground location is still missing, `background` is also reported as missing and flow ends
  4. background location permission is requested only after foreground is granted
- Callback result:
  - `ready`
  - `missing`
  - `requested`
  - `granted`

### snooze

- `snooze({ id, duration })` uses `duration` in seconds
- During active snooze window, both are suppressed for that geofence:
  - transition callback (`transitionReceived`)
  - notification display
- Snooze state is persisted on both platforms and survives app restart until expiration.

## How To Test

This plugin has native geofence behavior. Full validation requires a real device or emulator/simulator with location simulation.

### What you can test without a device

- TypeScript build (`npm run build`)
- API wiring and compile-time checks
- Host app integration and sync (`npx cap sync`)

### What requires device/emulator

- Real geofence enter/exit transitions
- Background behavior
- Killed-app behavior
- Notification delivery and notification click callback

### Recommended manual test flow

1. Verify current state with `checkPermissionStatus`
2. Start permission flow with `initialize(callback)`
   - `initialize` returns `true` if everything is already granted
   - `initialize` returns `false` if async permission flow started
   - Android and iOS now follow the same initialize sequence:
     - request notification permission if needed
     - request foreground location permission if needed
     - if foreground is still missing, `background` is also reported missing and flow ends
     - request background location permission only after foreground is granted
   - callback payload contains:
     - `requested: ("notification" | "location" | "background")[]`
     - `granted: ("notification" | "location" | "background")[]`
     - `missing: ("notification" | "location" | "background")[]`
     - `ready: boolean`
3. Add geofence: `addOrUpdate`
4. Verify persistence API: `getWatched`
5. Trigger location transition with real movement or mock route
6. Verify:
   - `transitionReceived` callback
   - notification shown
   - `notificationClicked` callback when tapped
7. Validate cleanup:
   - `remove` / `removeAll`
   - `dismissNotifications`
8. Validate `snooze`:
   - call `snooze({ id, duration })` (duration in seconds)
   - during snooze window, transition callback/notification for that geofence is suppressed

For platform-specific setup and edge-case validation, use:

- [Android Integration Checklist](./ANDROID_CHECKLIST.md)
- [iOS Integration Checklist](./IOS_CHECKLIST.md)

<docgen-index>

* [`checkPermissionStatus()`](#checkpermissionstatus)
* [`requestLocationPermission()`](#requestlocationpermission)
* [`requestBackgroundLocationPermission()`](#requestbackgroundlocationpermission)
* [`requestNotificationPermission()`](#requestnotificationpermission)
* [`initialize()`](#initialize)
* [`addOrUpdate(...)`](#addorupdate)
* [`remove(...)`](#remove)
* [`removeAll()`](#removeall)
* [`getWatched()`](#getwatched)
* [`dismissNotifications(...)`](#dismissnotifications)
* [`snooze(...)`](#snooze)
* [`deviceReady()`](#deviceready)
* [`ping()`](#ping)
* [`addListener('notificationClicked', ...)`](#addlistenernotificationclicked-)
* [`addListener('transitionReceived', ...)`](#addlistenertransitionreceived-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### checkPermissionStatus()

```typescript
checkPermissionStatus() => Promise<Record<string, string>>
```

**Returns:** <code>Promise&lt;<a href="#record">Record</a>&lt;string, string&gt;&gt;</code>

--------------------


### requestLocationPermission()

```typescript
requestLocationPermission() => Promise<Record<string, string>>
```

**Returns:** <code>Promise&lt;<a href="#record">Record</a>&lt;string, string&gt;&gt;</code>

--------------------


### requestBackgroundLocationPermission()

```typescript
requestBackgroundLocationPermission() => Promise<Record<string, string>>
```

**Returns:** <code>Promise&lt;<a href="#record">Record</a>&lt;string, string&gt;&gt;</code>

--------------------


### requestNotificationPermission()

```typescript
requestNotificationPermission() => Promise<Record<string, string>>
```

**Returns:** <code>Promise&lt;<a href="#record">Record</a>&lt;string, string&gt;&gt;</code>

--------------------


### initialize()

```typescript
initialize() => Promise<InitializeResult>
```

**Returns:** <code>Promise&lt;<a href="#initializeresult">InitializeResult</a>&gt;</code>

--------------------


### addOrUpdate(...)

```typescript
addOrUpdate(options: AddOrUpdateOptions) => Promise<void>
```

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#addorupdateoptions">AddOrUpdateOptions</a></code> |

--------------------


### remove(...)

```typescript
remove(options: RemoveOptions) => Promise<void>
```

| Param         | Type                                                    |
| ------------- | ------------------------------------------------------- |
| **`options`** | <code><a href="#removeoptions">RemoveOptions</a></code> |

--------------------


### removeAll()

```typescript
removeAll() => Promise<void>
```

--------------------


### getWatched()

```typescript
getWatched() => Promise<WatchedResult>
```

**Returns:** <code>Promise&lt;<a href="#watchedresult">WatchedResult</a>&gt;</code>

--------------------


### dismissNotifications(...)

```typescript
dismissNotifications(options: DismissNotificationsOptions) => Promise<void>
```

| Param         | Type                                                                                |
| ------------- | ----------------------------------------------------------------------------------- |
| **`options`** | <code><a href="#dismissnotificationsoptions">DismissNotificationsOptions</a></code> |

--------------------


### snooze(...)

```typescript
snooze(options: SnoozeOptions) => Promise<void>
```

| Param         | Type                                                    |
| ------------- | ------------------------------------------------------- |
| **`options`** | <code><a href="#snoozeoptions">SnoozeOptions</a></code> |

--------------------


### deviceReady()

```typescript
deviceReady() => Promise<void>
```

--------------------


### ping()

```typescript
ping() => Promise<void>
```

--------------------


### addListener('notificationClicked', ...)

```typescript
addListener(eventName: 'notificationClicked', listenerFunc: (event: NotificationClickedEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'notificationClicked'</code>                                                                |
| **`listenerFunc`** | <code>(event: <a href="#notificationclickedevent">NotificationClickedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('transitionReceived', ...)

```typescript
addListener(eventName: 'transitionReceived', listenerFunc: (event: TransitionReceivedEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                            |
| ------------------ | ----------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'transitionReceived'</code>                                                               |
| **`listenerFunc`** | <code>(event: <a href="#transitionreceivedevent">TransitionReceivedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### InitializeResult

| Prop            | Type                                 |
| --------------- | ------------------------------------ |
| **`ready`**     | <code>boolean</code>                 |
| **`missing`**   | <code>InitMissingPermission[]</code> |
| **`requested`** | <code>InitMissingPermission[]</code> |
| **`granted`**   | <code>InitMissingPermission[]</code> |


#### AddOrUpdateOptions

| Prop            | Type                    |
| --------------- | ----------------------- |
| **`geofences`** | <code>Geofence[]</code> |


#### Geofence

| Prop                 | Type                                                                  |
| -------------------- | --------------------------------------------------------------------- |
| **`id`**             | <code>string</code>                                                   |
| **`latitude`**       | <code>number</code>                                                   |
| **`longitude`**      | <code>number</code>                                                   |
| **`radius`**         | <code>number</code>                                                   |
| **`transitionType`** | <code>number</code>                                                   |
| **`loiteringDelay`** | <code>number</code>                                                   |
| **`notification`**   | <code><a href="#geofencenotification">GeofenceNotification</a></code> |
| **`url`**            | <code>string</code>                                                   |
| **`authorization`**  | <code>string</code>                                                   |
| **`startTime`**      | <code>string</code>                                                   |
| **`endTime`**        | <code>string</code>                                                   |


#### GeofenceNotification

| Prop                 | Type                  |
| -------------------- | --------------------- |
| **`id`**             | <code>number</code>   |
| **`title`**          | <code>string</code>   |
| **`text`**           | <code>string</code>   |
| **`vibrate`**        | <code>number[]</code> |
| **`icon`**           | <code>string</code>   |
| **`smallIcon`**      | <code>string</code>   |
| **`color`**          | <code>string</code>   |
| **`data`**           | <code>unknown</code>  |
| **`openAppOnClick`** | <code>boolean</code>  |
| **`frequency`**      | <code>number</code>   |
| **`lastTriggered`**  | <code>number</code>   |


#### RemoveOptions

| Prop      | Type                  |
| --------- | --------------------- |
| **`ids`** | <code>string[]</code> |


#### WatchedResult

| Prop            | Type                    |
| --------------- | ----------------------- |
| **`geofences`** | <code>Geofence[]</code> |


#### DismissNotificationsOptions

| Prop      | Type                  |
| --------- | --------------------- |
| **`ids`** | <code>number[]</code> |


#### SnoozeOptions

| Prop           | Type                |
| -------------- | ------------------- |
| **`id`**       | <code>string</code> |
| **`duration`** | <code>number</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### NotificationClickedEvent

| Prop       | Type                 |
| ---------- | -------------------- |
| **`data`** | <code>unknown</code> |


#### TransitionReceivedEvent

| Prop            | Type                    |
| --------------- | ----------------------- |
| **`geofences`** | <code>Geofence[]</code> |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{
 [P in K]: T;
 }</code>


#### InitMissingPermission

<code>'notification' | 'location' | 'background'</code>

</docgen-api>
