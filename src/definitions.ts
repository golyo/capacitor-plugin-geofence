import type { PluginListenerHandle } from '@capacitor/core';

export interface GeofenceNotification {
  id?: number;
  title?: string;
  text?: string;
  vibrate?: number[];
  icon?: string;
  smallIcon?: string;
  color?: string;
  data?: unknown;
  openAppOnClick?: boolean;
  frequency?: number;
  lastTriggered?: number;
}

export interface Geofence {
  id: string;
  latitude: number;
  longitude: number;
  radius: number;
  transitionType: number;
  loiteringDelay?: number;
  notification?: GeofenceNotification;
  url?: string;
  authorization?: string;
  startTime?: string;
  endTime?: string;
}

export interface AddOrUpdateOptions {
  geofences: Geofence[];
}

export interface RemoveOptions {
  ids: string[];
}

export interface DismissNotificationsOptions {
  ids: number[];
}

export interface SnoozeOptions {
  id: string;
  duration: number;
}

export interface WatchedResult {
  geofences: Geofence[];
}

export interface NotificationClickedEvent {
  data: unknown;
}

export interface TransitionReceivedEvent {
  geofences: Geofence[];
}

export type InitMissingPermission = 'notification' | 'location' | 'background';

export interface InitializeResult {
  ready: boolean;
  missing: InitMissingPermission[];
  requested: InitMissingPermission[];
  granted: InitMissingPermission[];
}

export interface GeofencePlugin {
  checkPermissionStatus(): Promise<Record<string, string>>;
  requestLocationPermission(): Promise<Record<string, string>>;
  requestBackgroundLocationPermission(): Promise<Record<string, string>>;
  requestNotificationPermission(): Promise<Record<string, string>>;
  initialize(): Promise<InitializeResult>;
  addOrUpdate(options: AddOrUpdateOptions): Promise<void>;
  remove(options: RemoveOptions): Promise<void>;
  removeAll(): Promise<void>;
  getWatched(): Promise<WatchedResult>;
  dismissNotifications(options: DismissNotificationsOptions): Promise<void>;
  snooze(options: SnoozeOptions): Promise<void>;
  deviceReady(): Promise<void>;
  ping(): Promise<void>;
  addListener(
    eventName: 'notificationClicked',
    listenerFunc: (event: NotificationClickedEvent) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'transitionReceived',
    listenerFunc: (event: TransitionReceivedEvent) => void,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}
