package com.assistant.main.helpers;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class AudioStreamer {

    private static final String TAG = "AudioStreamer";
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
    );
    private AudioRecord audioRecord;
    private WebSocket whisperWebSocket;
    private OkHttpClient whisperClient;
    private boolean isRecording = false;
    private ExecutorService executor;
    private String domain = "personai";
    private String whisperWebsocketUrl = "wss://whisper-" + domain +".pagekite.me/asr?android=true";
    private static final int CHUNK_DURATION_MS = 100;
    private static final int BYTES_PER_SAMPLE = 2; // PCM 16-bit
    private static final int CHANNELS = 1;
    private static final int CHUNK_SIZE = (SAMPLE_RATE * CHUNK_DURATION_MS / 1000) * BYTES_PER_SAMPLE * CHANNELS;

    // --- JSON Event Listener Handling ---
    public interface OnJsonMessageListener {
        void onMessage(JSONObject json) throws JSONException;
    }

    private final Set<OnJsonMessageListener> listeners = new HashSet<>();

    public void addOnJsonMessageListener(OnJsonMessageListener listener) {
        listeners.add(listener);
    }

    public void removeOnJsonMessageListener(OnJsonMessageListener listener) {
        listeners.remove(listener);
    }

    private void dispatchJsonMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            for (OnJsonMessageListener listener : listeners) {
                listener.onMessage(json);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON received: " + text, e);
        }
    }

    // --- WebSocket and Audio Streaming ---
    public void startStreaming(String domain) {
        connectWebSocket(domain);
    }

    private void connectWebSocket() {
        connectWebSocket(null);
    }
    private void connectWebSocket(String domain) {
        if(domain != null)
            this.domain = domain;
        this.whisperWebsocketUrl = "wss://whisper-" + domain +".pagekite.me/asr?android=true";
        whisperClient = new OkHttpClient();
        Request whisperRequest = new Request.Builder()
                .url(whisperWebsocketUrl)
                .build();
        whisperClient.newWebSocket(whisperRequest, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                AudioStreamer.this.whisperWebSocket = webSocket;
                Log.d(TAG, "WebSocket opened");
                startRecording();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket error: ", t);
                //reconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                stopRecording();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received: " + text);
                dispatchJsonMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "Received: " + bytes);
            }
        });
    }

    private void startRecording() {
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE
        );

        audioRecord.startRecording();
        isRecording = true;

        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            byte[] chunkBuffer = new byte[CHUNK_SIZE];
            int offset = 0;

            while (isRecording) {
                int remaining = CHUNK_SIZE - offset;
                int read = audioRecord.read(chunkBuffer, offset, remaining);

                if (read > 0) {
                    offset += read;

                    if (offset >= CHUNK_SIZE) {
                        ByteString audioData = ByteString.of(chunkBuffer, 0, CHUNK_SIZE);
                        if (whisperWebSocket != null) {
                            whisperWebSocket.send(audioData);
                        }
                        offset = 0; // reset for next chunk

                        // Ensure chunks are sent at real-time pace
                        try {
                            Thread.sleep(CHUNK_DURATION_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    Log.e(TAG, "AudioRecord read error: " + read);
                }
            }
        });
    }

    public void stopStreaming() {
        if (whisperWebSocket != null) {
            whisperWebSocket.close(1000, "User closed");
            whisperWebSocket = null;
        }
        stopRecording();
    }

    private void stopRecording() {
        isRecording = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void reconnect() {
        stopRecording();
        Log.d(TAG, "Reconnecting in 1 second...");
        new Handler(Looper.getMainLooper()).postDelayed(this::connectWebSocket, 1000);
    }
}