import { WebPlugin } from '@capacitor/core';

import type { AudioTogglePlugin } from './definitions';

export class AudioToggleWeb extends WebPlugin implements AudioTogglePlugin {
  async setAudioMode(options: { mode: string }): Promise<{ mode: string }> {
    return options;
  }

  bluetoothConnected(): Promise<{ isBluetooth: boolean }> {
    return Promise.resolve({ isBluetooth: false });
  }
}
