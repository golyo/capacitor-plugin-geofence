import { WebPlugin } from '@capacitor/core';

import type {
  AddOrUpdateOptions,
  DismissNotificationsOptions,
  Geofence,
  GeofencePlugin,
  InitializeResult,
  RemoveOptions,
  SnoozeOptions,
  WatchedResult,
} from './definitions';

export class GeofenceWeb extends WebPlugin implements GeofencePlugin {
  private geofences: Geofence[] = [];

  async checkPermissionStatus(): Promise<Record<string, string>> {
    return {
      location: 'prompt',
      backgroundLocation: 'prompt',
      notifications: 'prompt',
    };
  }

  async requestLocationPermission(): Promise<Record<string, string>> {
    return this.checkPermissionStatus();
  }

  async requestBackgroundLocationPermission(): Promise<Record<string, string>> {
    return this.checkPermissionStatus();
  }

  async requestNotificationPermission(): Promise<Record<string, string>> {
    return this.checkPermissionStatus();
  }

  async initialize(): Promise<InitializeResult> {
    return {
      ready: false,
      missing: ['notification', 'location', 'background'],
      requested: [],
      granted: [],
    };
  }

  async addOrUpdate(options: AddOrUpdateOptions): Promise<void> {
    const byId = new Map(this.geofences.map((item) => [item.id, item]));
    for (const geofence of options.geofences) {
      byId.set(geofence.id, geofence);
    }
    this.geofences = Array.from(byId.values());
  }

  async remove(options: RemoveOptions): Promise<void> {
    const toRemove = new Set(options.ids);
    this.geofences = this.geofences.filter((geofence) => !toRemove.has(geofence.id));
  }

  async removeAll(): Promise<void> {
    this.geofences = [];
  }

  async getWatched(): Promise<WatchedResult> {
    return { geofences: [...this.geofences] };
  }

  async dismissNotifications(options: DismissNotificationsOptions): Promise<void> {
    void options;
    return;
  }

  async snooze(options: SnoozeOptions): Promise<void> {
    void options;
    return;
  }

  async deviceReady(): Promise<void> {
    return;
  }

  async ping(): Promise<void> {
    return;
  }
}
