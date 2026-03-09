import { registerPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

import type {
  Geofence as GeofenceShape,
  GeofencePlugin,
  InitializeResult,
  NotificationClickedEvent,
  TransitionReceivedEvent,
} from './definitions';

const GeofenceNative = registerPlugin<GeofencePlugin>('Geofence', {
  web: () => import('./web').then((m) => new m.GeofenceWeb()),
});

class GeofenceApi {
  onNotificationClicked: (notificationData: unknown) => void = () => undefined;

  onTransitionReceived: (geofences: GeofenceShape[]) => void = (geofences: GeofenceShape[]) => {
    this.receiveTransition(geofences);
  };

  // Kept for Cordova compatibility.
  receiveTransition: (geofences: GeofenceShape[]) => void = () => undefined;

  private notificationClickedHandle?: PluginListenerHandle;
  private transitionReceivedHandle?: PluginListenerHandle;

  async initialize(
    callback?: (result: InitializeResult) => void,
    success?: (ready: boolean) => void,
    error?: (reason: unknown) => void,
  ): Promise<boolean> {
    await this.attachNativeListeners();
    const before = await this.checkPermissionStatus();
    const hasMissingAtStart = getMissingPermissions(before).length > 0;
    if (hasMissingAtStart) {
      GeofenceNative.initialize()
        .then((result) => {
          if (typeof callback === 'function') {
            callback(result);
          }
        })
        .catch((reason) => {
          if (typeof error === 'function') {
            error(reason);
          }
        });
      if (typeof success === 'function') {
        success(false);
      }
      return false;
    }
    return this.wrapPromise(
      GeofenceNative.initialize().then((result) => {
        if (typeof callback === 'function') {
          callback(result);
        }
        return result.ready;
      }),
      success,
      error,
    );
  }

  async checkPermissionStatus(): Promise<Record<string, string>> {
    return GeofenceNative.checkPermissionStatus();
  }

  async requestLocationPermission(): Promise<Record<string, string>> {
    return GeofenceNative.requestLocationPermission();
  }

  async requestBackgroundLocationPermission(): Promise<Record<string, string>> {
    return GeofenceNative.requestBackgroundLocationPermission();
  }

  async requestNotificationPermission(): Promise<Record<string, string>> {
    return GeofenceNative.requestNotificationPermission();
  }

  async addOrUpdate(
    geofences: GeofenceShape | GeofenceShape[],
    success?: (result: void) => void,
    error?: (reason: unknown) => void,
  ): Promise<void> {
    const geofenceList = Array.isArray(geofences) ? geofences : [geofences];
    geofenceList.forEach(coerceProperties);
    return this.wrapPromise(GeofenceNative.addOrUpdate({ geofences: geofenceList }), success, error);
  }

  async remove(
    ids: string | number | Array<string | number>,
    success?: (result: void) => void,
    error?: (reason: unknown) => void,
  ): Promise<void> {
    const idList = (Array.isArray(ids) ? ids : [ids]).map((id) => id.toString());
    return this.wrapPromise(GeofenceNative.remove({ ids: idList }), success, error);
  }

  async removeAll(success?: (result: void) => void, error?: (reason: unknown) => void): Promise<void> {
    return this.wrapPromise(GeofenceNative.removeAll(), success, error);
  }

  async getWatched(
    success?: (result: GeofenceShape[]) => void,
    error?: (reason: unknown) => void,
  ): Promise<GeofenceShape[]> {
    return this.wrapPromise(
      GeofenceNative.getWatched().then((result) => result.geofences),
      success,
      error,
    );
  }

  async dismissNotifications(ids: number | number[]): Promise<void> {
    const idList = Array.isArray(ids) ? ids : [ids];
    await GeofenceNative.dismissNotifications({ ids: idList });
  }

  async snooze(id: string | number, duration: number): Promise<void> {
    await GeofenceNative.snooze({ id: id.toString(), duration });
  }

  async ping(success?: (result: void) => void, error?: (reason: unknown) => void): Promise<void> {
    return this.wrapPromise(GeofenceNative.ping(), success, error);
  }

  async deviceReady(): Promise<void> {
    await GeofenceNative.deviceReady();
  }

  private async attachNativeListeners(): Promise<void> {
    if (!this.notificationClickedHandle) {
      this.notificationClickedHandle = await GeofenceNative.addListener(
        'notificationClicked',
        (event: NotificationClickedEvent) => {
          this.onNotificationClicked(event.data);
        },
      );
    }
    if (!this.transitionReceivedHandle) {
      this.transitionReceivedHandle = await GeofenceNative.addListener(
        'transitionReceived',
        (event: TransitionReceivedEvent) => {
          this.onTransitionReceived(event.geofences);
        },
      );
    }
  }

  private async wrapPromise<T>(
    promise: Promise<T>,
    success?: (result: T) => void,
    error?: (reason: unknown) => void,
  ): Promise<T> {
    try {
      const result = await promise;
      if (typeof success === 'function') {
        success(result);
      }
      return result;
    } catch (reason) {
      if (typeof error === 'function') {
        error(reason);
      }
      throw reason;
    }
  }
}

function coerceProperties(geofence: GeofenceShape): void {
  if (geofence.id) {
    geofence.id = geofence.id.toString();
  } else {
    throw new Error('Geofence id is not provided');
  }

  geofence.latitude = coerceNumber('Geofence latitude', geofence.latitude);
  geofence.longitude = coerceNumber('Geofence longitude', geofence.longitude);
  geofence.radius = coerceNumber('Geofence radius', geofence.radius);
  geofence.transitionType = coerceNumber('Geofence transitionType', geofence.transitionType);

  if (geofence.notification) {
    if (geofence.notification.id !== undefined) {
      geofence.notification.id = coerceNumber('Geofence notification.id', geofence.notification.id);
    }
    if (geofence.notification.title !== undefined) {
      geofence.notification.title = geofence.notification.title.toString();
    }
    if (geofence.notification.text !== undefined) {
      geofence.notification.text = geofence.notification.text.toString();
    }
    if (geofence.notification.smallIcon !== undefined) {
      geofence.notification.smallIcon = geofence.notification.smallIcon.toString();
    }
    if (geofence.notification.openAppOnClick !== undefined) {
      geofence.notification.openAppOnClick = coerceBoolean(
        'Geofence notification.openAppOnClick',
        geofence.notification.openAppOnClick,
      );
    }
    if (Array.isArray(geofence.notification.vibrate)) {
      geofence.notification.vibrate = geofence.notification.vibrate.map((value, index) =>
        coerceInteger(`Geofence notification.vibrate[${index}]`, value),
      );
    }
  }
}

function coerceNumber(name: string, value: unknown): number {
  if (typeof value === 'number') {
    return value;
  }
  const converted = Number(value);
  if (Number.isNaN(converted)) {
    throw new Error(`Cannot convert ${name} to number`);
  }
  return converted;
}

function coerceInteger(name: string, value: unknown): number {
  const numberValue = coerceNumber(name, value);
  if (!Number.isInteger(numberValue)) {
    return parseInt(numberValue.toString(), 10);
  }
  return numberValue;
}

function coerceBoolean(_name: string, value: unknown): boolean {
  return Boolean(value);
}

function getMissingPermissions(status: Record<string, string>): string[] {
  const missing: string[] = [];
  if (status.notifications !== 'granted') {
    missing.push('notification');
  }
  if (status.location !== 'granted') {
    missing.push('location');
    missing.push('background');
    return missing;
  }
  if (status.backgroundLocation !== 'granted') {
    missing.push('background');
  }
  return missing;
}

export * from './definitions';
export * from './TransitionType';
export { GeofenceNative, GeofenceApi };
export const geofence = new GeofenceApi();
export const Geofence = geofence;
