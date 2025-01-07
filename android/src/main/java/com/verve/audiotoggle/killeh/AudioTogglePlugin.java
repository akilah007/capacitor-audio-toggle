package com.verve.audiotoggle.killeh;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@CapacitorPlugin(name = "AudioToggle")
public class AudioTogglePlugin extends Plugin {
    private static final String TAG = "AudioToggle";
    Bridge audioBridge;
    private BluetoothProfile.ServiceListener mProfileListener;
    private AudioManager mAudioManager;
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mIsBluetoothConnected;
    private boolean mIsScoConnected;
    private String audioMode;

    @Override
    public void load() {
        audioBridge = bridge;
        IntentFilter filter = new IntentFilter();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        filter.addCategory(
                BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY
                        + "."
                        + BluetoothAssignedNumbers.PLANTRONICS);
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);
        getContext().registerReceiver(mReceiver, filter);
        Log.w("BluetoothManager", "Register Receiver");
    }

    private void refreshCallView() {
        if (audioMode == null) {
            audioMode = "unknown"; // Set a default value if audioMode is null
        }
        JSObject actionJson = new JSObject();
        actionJson.put("audioMode", audioMode);

        actionJson.put("bluetoothConnected", mIsBluetoothConnected);
        actionJson.put("audioMode", audioMode);
        actionJson.put("deviceConnected", getInitialDeviceConnected());
        if (audioBridge != null) {
            notifyListeners("bluetoothConnected", actionJson, true);
            audioBridge.triggerJSEvent("bluetoothConnected", "document");
        }
        android.util.Log.d("BluetoothManager", "[Bluetooth] mIsBluetoothConnected: " + mIsBluetoothConnected + " mIsScoConnected " + mIsScoConnected + " mBluetoothAdapter " + getInitialDeviceConnected());

    }
    private boolean getInitialDeviceConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                return mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED;
            }
            return false;
        }
        return false;
    }
    private void startBluetooth() {
        if (mIsBluetoothConnected) {
            android.util.Log.e("BluetoothManager", "[Bluetooth] Already started, skipping...");
            return;
        }

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            if (mProfileListener != null) {
                android.util.Log.w(
                        "BluetoothManager",
                        "[Bluetooth] Headset profile was already opened, let's close it");
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            }

            mProfileListener =
                    new BluetoothProfile.ServiceListener() {
                        public void onServiceConnected(int profile, BluetoothProfile proxy) {
                            if (profile == BluetoothProfile.HEADSET) {
                                android.util.Log.d(
                                        "BluetoothManager", "[Bluetooth] Headset connected");
                                mBluetoothHeadset = (BluetoothHeadset) proxy;
                                mIsBluetoothConnected = true;
                                bluetoothMode();
                                refreshCallView();
                            }
                        }

                        public void onServiceDisconnected(int profile) {
                            if (profile == BluetoothProfile.HEADSET) {
                                mBluetoothHeadset = null;
                                mIsBluetoothConnected = false;
                                android.util.Log.d(
                                        "BluetoothManager", "[Bluetooth] Headset disconnected");
                                refreshCallView();
                            }
                        }
                    };
            boolean success =
                    mBluetoothAdapter.getProfileProxy(
                            getContext(), mProfileListener, BluetoothProfile.HEADSET);
            if (!success) {
                android.util.Log.e("BluetoothManager", "[Bluetooth] getProfileProxy failed !");
            }
        } else {
            android.util.Log.w("BluetoothManager", "[Bluetooth] Interface disabled on device");
        }
    }

    private void disableBluetoothSCO() {
        if (mAudioManager != null && mAudioManager.isBluetoothScoOn()) {
            mAudioManager.stopBluetoothSco();
            mAudioManager.setBluetoothScoOn(false);

            // Hack to ensure bluetooth sco is really stopped
            int retries = 0;
            while (mIsScoConnected && retries < 10) {
                retries++;

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
//                    Log.e(e);
                }
                mAudioManager.stopBluetoothSco();
                mAudioManager.setBluetoothScoOn(false);
            }
            android.util.Log.w("BluetoothManager", "[Bluetooth] SCO disconnected!");
        }
    }
    private void stopBluetooth() {
        android.util.Log.w("BluetoothManager", "[Bluetooth] Stopping...");
        mIsBluetoothConnected = false;

        disableBluetoothSCO();

        if (mBluetoothAdapter != null && mProfileListener != null && mBluetoothHeadset != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            mProfileListener = null;
        }
//        BluetoothDevice mBluetoothDevice = null;

        android.util.Log.w("BluetoothManager", "[Bluetooth] Stopped!");
    }
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private static final String TAG = "Bluetooth Receiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            android.util.Log.d("BluetoothManager", "Receiver");

            if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
                int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    android.util.Log.d("BluetoothManager", "[Bluetooth] SCO state: connected");
                    //				LinphoneManager.getInstance().audioStateChanged(AudioState.BLUETOOTH);
                    mIsScoConnected = true;
                } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                    android.util.Log.d("BluetoothManager", "[Bluetooth] SCO state: disconnected");
                    //				LinphoneManager.getInstance().audioStateChanged(AudioState.SPEAKER);
                    mIsScoConnected = false;
                } else if (state == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
                    android.util.Log.d("BluetoothManager", "[Bluetooth] SCO state: connecting");
                    //				LinphoneManager.getInstance().audioStateChanged(AudioState.BLUETOOTH);
//                    mIsScoConnected = true;
                } else {
                    android.util.Log.d("BluetoothManager", "[Bluetooth] SCO state: " + state);
                }
                refreshCallView();
            } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state =
                        intent.getIntExtra(
                                BluetoothAdapter.EXTRA_CONNECTION_STATE,
                                BluetoothAdapter.STATE_DISCONNECTED);
                if (state == 0) {
                    android.util.Log.d("BluetoothManager", "[Bluetooth] State: disconnected");
                    stopBluetooth();
                } else if (state == 2) {
                    android.util.Log.d("BluetoothManager", "[Bluetooth] State: connected");
                    startBluetooth();
                } else {
                    android.util.Log.d("BluetoothManager", "[Bluetooth] State: " + state);
                }
                refreshCallView();
            } else if (intent.getAction()
                    .equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
                String command =
                        intent.getExtras()
                                .getString(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
                // int type =
                // intent.getExtras().getInt(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE);

                Object[] args =
                        (Object[])
                                intent.getExtras()
                                        .get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
                if (args == null || args.length <= 0) {
                    android.util.Log.d(
                            "BluetoothManager", "[Bluetooth] Event: " + command + ", no args");
                    return;
                }
                String eventName = (args[0]).toString();
                if (eventName.equals("BUTTON") && args.length >= 3) {
                    String buttonID = args[1].toString();
                    String mode = args[2].toString();
                    android.util.Log.d(
                            "BluetoothManager",
                            "[Bluetooth] Event: "
                                    + command
                                    + " : "
                                    + eventName
                                    + ", id = "
                                    + buttonID
                                    + " ("
                                    + mode
                                    + ")");
                } else {
                    android.util.Log.d(
                            "BluetoothManager", "[Bluetooth] Event: " + command + " : " + eventName);
                }
                refreshCallView();
            }
        }
    };

    private void bluetoothMode() {
        if (getInitialDeviceConnected()) {
            startBluetooth();
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mAudioManager.startBluetoothSco();
            mAudioManager.setBluetoothScoOn(true);
            mAudioManager.setSpeakerphoneOn(false);
//        Toast.makeText(getContext(), "Bluetooth Connected", Toast.LENGTH_SHORT).show();
            audioMode = "bluetooth";
        }
    }
    @PluginMethod
    public void bluetoothConnected(PluginCall call) {
        JSObject actionJson = new JSObject();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        actionJson.put("isConnected", mAudioManager.isBluetoothScoOn());
        if (mAudioManager.isBluetoothScoOn()) {
            mIsBluetoothConnected = true;
            bluetoothMode();
        }
        refreshCallView();
        call.resolve(actionJson);
    }
    @PluginMethod
    public void setAudioMode(PluginCall call) {
        String mode = call.getString("mode");
        Context context = getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
            int result = mAudioManager.requestAudioFocus(focusRequest);
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Handle audio focus request failure here
                Log.d(TAG, "Audio Request Error");
            }
        } else {
            mAudioManager.requestAudioFocus(null, AudioAttributes.USAGE_VOICE_COMMUNICATION,AudioManager.AUDIOFOCUS_GAIN);
        }
        AudioDeviceInfo speakerDevice = null;
        mAudioManager.setSpeakerphoneOn(false);
        AudioDeviceInfo[] d = new AudioDeviceInfo[0];
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            d = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : d) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    if(mAudioManager.isBluetoothScoOn()) {
//                        Toast.makeText(context, "Bluetooth Connected", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        if ("bluetooth".equals(mode)) {
            startBluetooth();
            bluetoothMode();
        }
        if ("earpiece".equals(mode)) {
            mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
            audioMode = "earpiece";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                mAudioManager.clearCommunicationDevice();
                List<AudioDeviceInfo> devices = mAudioManager.getAvailableCommunicationDevices();
                for (AudioDeviceInfo device : devices) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                        speakerDevice = device;
                        break;
                    }
                }
                // Your existing code to set communication device
                boolean result = mAudioManager.setCommunicationDevice(speakerDevice);
            } else {
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                mAudioManager.setSpeakerphoneOn(false);
            }

        } else if ("speaker".equals(mode)) {
            audioMode = "speaker";
            if (mAudioManager.isBluetoothScoOn()) {
                Log.d(TAG, "I'm in!!!" + mode);
                stopBluetooth();
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                mAudioManager.stopBluetoothSco();
                mAudioManager.setBluetoothScoOn(false);
                mAudioManager.setSpeakerphoneOn(true);
            }
            mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                List<AudioDeviceInfo> devices = mAudioManager.getAvailableCommunicationDevices();
                for (AudioDeviceInfo device : devices) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        speakerDevice = device;
                        break;
                    }
                }
                boolean result = mAudioManager.setCommunicationDevice(speakerDevice);
            } else {
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                mAudioManager.setSpeakerphoneOn(true);
            }
            Toast.makeText(context, "Speaker on", Toast.LENGTH_SHORT).show();

        } else if ("ringtone".equals(mode)) {
            Log.d(TAG, "I'm in!!!" + mode);
            mAudioManager.setMode(AudioManager.MODE_RINGTONE);
            mAudioManager.setSpeakerphoneOn(false);
//            Toast.makeText(context, mode + " " + audioManager.isSpeakerphoneOn(), Toast.LENGTH_SHORT).show();

        } else if ("normal".equals(mode)) {
            Log.d(TAG, "I'm in!!!" + mode);
            mAudioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
            mAudioManager.setMode(AudioManager.MODE_NORMAL);
            mAudioManager.setSpeakerphoneOn(false);
//            Toast.makeText(context, mode + " " + audioManager.isSpeakerphoneOn(), Toast.LENGTH_SHORT).show();
        }
    }

}
