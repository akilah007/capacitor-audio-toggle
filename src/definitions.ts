import { PluginListenerHandle } from '@capacitor/core';

export interface AudioTogglePlugin {
  setAudioMode(options: { mode: string }): Promise< { mode: string }>;
  bluetoothConnected(): Promise< { isBluetooth: boolean }>;
  addListener(eventName: 'bluetoothConnected', listenerFunc: (bluetoothConnected: boolean) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
}
