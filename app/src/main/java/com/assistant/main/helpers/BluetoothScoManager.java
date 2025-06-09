package com.assistant.main.helpers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * A singleton manager for handling the Bluetooth SCO audio connection lifecycle.
 *
 * This manager operates as a state machine to robustly handle SCO connections.
 * It can be started to actively seek and maintain an SCO connection and will
 * fire events to a listener when the connection state changes.
 *
 * NOTE: Even without recording, starting an SCO connection requires the
 * RECORD_AUDIO and MODIFY_AUDIO_SETTINGS permissions.
 *
 * --- USAGE ---
 * 1. Add permissions to AndroidManifest.xml:
 *    <uses-permission android:name="android.permission.RECORD_AUDIO" />
 *    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
 *    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> for API 31+
 *
 * 2. Get instance: BluetoothScoManager.getInstance(context)
 * 3. Set listener: manager.setListener(myListener)
 * 4. Start managing connection: manager.start()
 * 5. Stop managing connection: manager.stop()
 * 6. Check current state: manager.getScoState()
 */
public final class BluetoothScoManager {

    private static final String TAG = "BluetoothScoManager";
    private static final int RECONNECT_DELAY_MS = 3000;

    private static volatile BluetoothScoManager instance;

    private final Context context;
    private final AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // --- State Management ---
    public enum ScoState {
        STOPPED,      // Manager is inactive and not trying to connect.
        DISCONNECTED, // Manager is active, but SCO is not connected (will try to reconnect).
        CONNECTING,   // SCO connection in progress.
        CONNECTED     // SCO is active and ready.
    }
    private volatile ScoState currentState = ScoState.STOPPED;

    // --- Listener for State Changes ---
    public interface ScoConnectionListener {
        /**
         * Fired when the SCO connection state changes.
         * @param newState The new state of the SCO connection.
         */
        void onScoStateChanged(@NonNull ScoState newState);

        /**
         * Fired when an error occurs during connection.
         * @param errorMessage A description of the error.
         */
        void onError(@NonNull String errorMessage);
    }
    private ScoConnectionListener listener;

    // --- Public Interface ---

    public static BluetoothScoManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BluetoothScoManager.class) {
                if (instance == null) {
                    instance = new BluetoothScoManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private BluetoothScoManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Sets the listener to receive state changes and error events.
     */
    public void setListener(ScoConnectionListener listener) {
        this.listener = listener;
    }

    /**
     * Gets the current state of the SCO connection manager.
     */
    public ScoState getScoState() {
        return currentState;
    }

    /**
     * Checks if a Bluetooth SCO-compatible headset is paired and available.
     * Note: This does not mean the SCO audio channel is active.
     */
    public boolean isBluetoothHeadsetAvailable() {
        return audioManager.isBluetoothScoAvailableOffCall();
    }

    /**
     * Starts the manager. It will begin listening for SCO state changes
     * and will attempt to connect if a headset is available. It will
     * automatically try to reconnect if the connection is lost.
     */
    public void start() {
        Log.d(TAG, "BluetoothScoManager starting...");
        if (currentState != ScoState.STOPPED) {
            Log.w(TAG, "Manager is already started. Ignoring call.");
            return;
        }

        // Register receiver first to catch any immediate state changes
        context.registerReceiver(scoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));

        if (!isBluetoothHeadsetAvailable()) {
            Log.w(TAG, "No Bluetooth headset available. Manager will listen for one.");
            // We are now in a state where we are "trying" but disconnected.
            updateState(ScoState.DISCONNECTED);
        } else {
            // A headset is available, so let's try to connect.
            Log.d(TAG, "Headset available. Attempting to start SCO connection.");
            updateState(ScoState.CONNECTING);
            audioManager.startBluetoothSco();
        }
    }

    /**
     * Stops the manager. It will disconnect any active SCO connection
     * and will stop trying to reconnect.
     */
    public void stop() {
        Log.d(TAG, "BluetoothScoManager stopping...");
        if (currentState == ScoState.STOPPED) {
            return;
        }

        // Stop any pending reconnect attempts
        handler.removeCallbacks(reconnectRunnable);

        // Disconnect SCO if it's on
        if (audioManager.isBluetoothScoOn()) {
            audioManager.stopBluetoothSco();
        }

        // Unregister the receiver
        try {
            context.unregisterReceiver(scoReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver was not registered or already unregistered.");
        }

        updateState(ScoState.STOPPED);
    }

    // --- Internal Logic ---

    /**
     * The single point of truth for changing the state.
     * This ensures we only fire events when the state actually changes.
     */
    private void updateState(ScoState newState) {
        if (currentState == newState) {
            return; // Fire events only on state change
        }

        Log.i(TAG, "SCO state changed: " + currentState + " -> " + newState);
        currentState = newState;

        // Notify listener on the main thread
        handler.post(() -> {
            if (listener != null) {
                listener.onScoStateChanged(currentState);
            }
        });

        // Handle reconnection logic
        handler.removeCallbacks(reconnectRunnable); // Always cancel pending reconnects on a state change
        if (currentState == ScoState.DISCONNECTED) {
            // If we are in the DISCONNECTED state, it means we should be trying to connect.
            // This is our robust "keep looking for SCO" logic.
            handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        }
    }

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            // This check ensures we only try to reconnect if the manager is in the right state.
            if (currentState == ScoState.DISCONNECTED) {
                if (isBluetoothHeadsetAvailable()) {
                    Log.i(TAG, "Reconnecting: Headset available, attempting to start SCO.");
                    audioManager.startBluetoothSco();
                    // The broadcast receiver will handle the transition to CONNECTING/CONNECTED
                } else {
                    Log.d(TAG, "Reconnecting: No headset available, will check again in " + RECONNECT_DELAY_MS + "ms.");
                    // Post again to keep checking
                    handler.postDelayed(this, RECONNECT_DELAY_MS);
                }
            }
        }
    };

    private final BroadcastReceiver scoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
            Log.d(TAG, "Received SCO Audio state update: " + state);

            switch (state) {
                case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                    updateState(ScoState.CONNECTING);
                    break;

                case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                    updateState(ScoState.CONNECTED);
                    break;

                case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                    // If the manager is meant to be active (i.e., not STOPPED),
                    // move to DISCONNECTED to trigger reconnection attempts.
                    if (currentState != ScoState.STOPPED) {
                        updateState(ScoState.DISCONNECTED);
                    }
                    break;

                case AudioManager.SCO_AUDIO_STATE_ERROR:
                    Log.e(TAG, "SCO audio connection error.");
                    if (listener != null) {
                        handler.post(() -> listener.onError("Failed to connect to Bluetooth headset."));
                    }
                    if (currentState != ScoState.STOPPED) {
                        updateState(ScoState.DISCONNECTED);
                    }
                    break;
            }
        }
    };
}
