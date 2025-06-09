package com.assistant.main.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.logging.Handler;

/**
 * A singleton manager for handling recording from a Bluetooth SCO headset.
 *
 * --- USAGE ---
 * 1. Add required permissions to AndroidManifest.xml:
 *    <uses-permission android:name="android.permission.RECORD_AUDIO" />
 *    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
 *    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" /> for API 31+
 *
 * 2. Request runtime permissions for RECORD_AUDIO and BLUETOOTH_CONNECT in your Activity/Fragment.
 *
 * 3. Get the instance:
 *    BluetoothMicManager manager = BluetoothMicManager.getInstance(getApplicationContext());
 *
 * 4. Start recording:
 *    manager.startRecording("path/to/output.3gp", new BluetoothMicManager.BluetoothMicCallback() { ... });
 *
 * 5. Stop recording:
 *    manager.stopRecording();
 *
 * --- HOW IT WORKS ---
 * It manages the asynchronous Bluetooth SCO connection lifecycle. When startRecording() is called,
 * it requests an SCO connection. A BroadcastReceiver listens for the connection to be established.
 * Only once the SCO connection is active does it start the MediaRecorder.
 */
public final class BluetoothMicManager {

    private static final String TAG = "BluetoothMicManager";

    // Singleton instance
    private static volatile BluetoothMicManager instance;

    private final Context context;
    private final AudioManager audioManager;

    private MediaRecorder mediaRecorder;
    private BluetoothMicCallback callback;

    private boolean isRecording = false;
    private boolean isScoConnecting = false;
    private String pendingOutputFile;

    private int state;

    // --- Public Interface ---

    public interface BluetoothMicCallback {
        void onBluetoothHeadsetAvailable();
        void onRecordingStarted();
        void onRecordingStopped();
        void onError(String errorMessage);
        void onStatusUpdate(String statusMessage);
    }

    public static BluetoothMicManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BluetoothMicManager.class) {
                if (instance == null) {
                    instance = new BluetoothMicManager(context);
                }
            }
        }
        return instance;
    }

    private BluetoothMicManager(Context context) {
        // Use application context to avoid leaking Activity context
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Checks if a Bluetooth SCO-compatible headset is connected.
     */
    public boolean isBluetoothHeadsetAvailable() {
        return audioManager.isBluetoothScoAvailableOffCall();
    }

    /**
     * Starts the process of recording from the Bluetooth headset.
     *
     * @param outputFile The path to the file where the audio will be saved.
     * @param callback   The callback to report status and errors.
     */
    public void startRecording(String outputFile, BluetoothMicCallback callback) {
        if (isRecording || isScoConnecting) {
            if (callback != null) {
                callback.onError("Already recording or connecting.");
            }
            return;
        }

        if (!isBluetoothHeadsetAvailable()) {
            if (callback != null) {
                callback.onError("No Bluetooth headset available.");
            }
            return;
        }

        this.callback = callback;
        this.pendingOutputFile = outputFile;
        isScoConnecting = true;

        if (callback != null) {
            callback.onStatusUpdate("Connecting to Bluetooth headset...");
        }

        // Register the receiver and start the SCO connection
        context.registerReceiver(scoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
        audioManager.startBluetoothSco();
        new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
            callback.onStatusUpdate(state == audioManager.SCO_AUDIO_STATE_CONNECTED ? "CONNECTED" : "DISCONNECTED");
        }, 2000);
    }

    /**
     * Stops the current recording.
     */
    public void stopRecording() {
        if (!isRecording && !isScoConnecting) {
            return; // Nothing to stop
        }

        Log.d(TAG, "Stopping recording and SCO connection.");

        // Stop the MediaRecorder if it's running
        if (mediaRecorder != null) {
            try {
                if (isRecording) {
                    mediaRecorder.stop();
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "MediaRecorder stop failed", e);
            } finally {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }

        // Stop the SCO connection
        if (audioManager.isBluetoothScoOn()) {
            audioManager.stopBluetoothSco();
        }

        // Unregister the receiver
        try {
            context.unregisterReceiver(scoReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "SCO receiver was not registered or already unregistered.");
        }

        boolean wasRecording = isRecording;
        isRecording = false;
        isScoConnecting = false;

        if (callback != null && wasRecording) {
            callback.onRecordingStopped();
        }
        this.callback = null; // Clear callback
    }


    // --- Internal Logic ---

    private final BroadcastReceiver scoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);

            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                Log.i(TAG, "Bluetooth SCO audio connected.");
                isScoConnecting = false;
                callback.onBluetoothHeadsetAvailable();
                //startActualMediaRecorder(pendingOutputFile);
            } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                Log.i(TAG, "Bluetooth SCO audio disconnected.");
                // If it disconnects while recording, stop everything.
                if (isRecording) {
                    stopRecording();
                    if (callback != null) {
                        callback.onError("Bluetooth connection lost during recording.");
                    }
                }
                isScoConnecting = false;
            } else if (state == AudioManager.SCO_AUDIO_STATE_ERROR) {
                Log.e(TAG, "Bluetooth SCO audio connection error.");
                if (callback != null) {
                    callback.onError("Failed to connect to Bluetooth headset.");
                }
                isScoConnecting = false;
            }
        }
    };

    private void startActualMediaRecorder(String outputFile) {
        if (mediaRecorder != null) {
            mediaRecorder.release();
        }

        mediaRecorder = new MediaRecorder();
        // IMPORTANT: The system automatically routes the MIC source from the SCO headset
        // once the SCO connection is active.
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(outputFile);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.i(TAG, "MediaRecorder started successfully.");
            if (callback != null) {
                callback.onRecordingStarted();
            }
        } catch (IOException e) {
            Log.e(TAG, "MediaRecorder preparation failed", e);
            if (callback != null) {
                callback.onError("Failed to start recorder: " + e.getMessage());
            }
            // Clean up on failure
            stopRecording();
        }
    }
}
//example activity usage
//
//import android.Manifest;
//        import android.content.pm.PackageManager;
//        import android.os.Bundle;
//        import android.util.Log;
//        import android.widget.Button;
//        import android.widget.TextView;
//        import android.widget.Toast;
//
//        import androidx.annotation.NonNull;
//        import androidx.appcompat.app.AppCompatActivity;
//        import androidx.core.app.ActivityCompat;
//
//public class MainActivity extends AppCompatActivity {
//
//    private static final int REQUEST_PERMISSIONS = 201;
//    private Button startStopButton;
//    private TextView statusTextView;
//    private BluetoothMicManager bluetoothMicManager;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main); // Layout with a Button and TextView
//
//        startStopButton = findViewById(R.id.startStopButton);
//        statusTextView = findViewById(R.id.statusTextView);
//
//        // Get the singleton instance
//        bluetoothMicManager = BluetoothMicManager.getInstance(this);
//
//        startStopButton.setOnClickListener(v -> {
//            if (bluetoothMicManager.isRecording()) {
//                bluetoothMicManager.stopRecording();
//            } else {
//                // Check permissions before starting
//                if (checkPermissions()) {
//                    startBluetoothRecording();
//                }
//            }
//        });
//    }
//
//    private void startBluetoothRecording() {
//        String outputFile = getExternalCacheDir().getAbsolutePath() + "/bt_recording.3gp";
//
//        statusTextView.setText("Status: Starting...");
//        startStopButton.setEnabled(false);
//
//        bluetoothMicManager.startRecording(outputFile, new BluetoothMicManager.BluetoothMicCallback() {
//            @Override
//            public void onRecordingStarted() {
//                runOnUiThread(() -> {
//                    statusTextView.setText("Status: Recording...");
//                    startStopButton.setText("Stop Recording");
//                    startStopButton.setEnabled(true);
//                    Toast.makeText(MainActivity.this, "Recording started!", Toast.LENGTH_SHORT).show();
//                });
//            }
//
//            @Override
//            public void onRecordingStopped() {
//                runOnUiThread(() -> {
//                    statusTextView.setText("Status: Idle");
//                    startStopButton.setText("Start Recording");
//                    startStopButton.setEnabled(true);
//                    Toast.makeText(MainActivity.this, "Recording saved to " + outputFile, Toast.LENGTH_LONG).show();
//                });
//            }
//
//            @Override
//            public void onError(String errorMessage) {
//                runOnUiThread(() -> {
//                    statusTextView.setText("Status: Error");
//                    startStopButton.setText("Start Recording");
//                    startStopButton.setEnabled(true);
//                    Toast.makeText(MainActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
//                });
//            }
//
//            @Override
//            public void onStatusUpdate(String statusMessage) {
//                runOnUiThread(() -> statusTextView.setText("Status: " + statusMessage));
//            }
//        });
//    }
//
//    // --- Permissions Handling ---
//
//    private boolean checkPermissions() {
//        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
//                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_PERMISSIONS);
//            return false;
//        }
//        return true;
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == REQUEST_PERMISSIONS) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission granted, now we can try to record
//                startBluetoothRecording();
//            } else {
//                Toast.makeText(this, "Permissions are required to record.", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        // Ensure recording is stopped if the activity is destroyed
//        if (bluetoothMicManager.isRecording()) {
//            bluetoothMicManager.stopRecording();
//        }
//    }
//}