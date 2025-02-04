package com.assistant.main;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.MutableLiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.assistant.main.camera.Camera;
import com.assistant.main.helpers.Beeper;
import com.assistant.main.helpers.FileTypeChecker;
import com.assistant.main.helpers.FileUtils;
import com.assistant.main.helpers.HttpRequestHelper;
import com.assistant.main.helpers.IP;
import com.assistant.main.helpers.ImagePopup;
import com.assistant.main.helpers.ImageUtils;
import com.assistant.main.helpers.VideoPopup;
import com.assistant.main.llm.LLM;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.parser.JSONObjectParser;

import org.apache.commons.io.IOUtils;
import org.bytedeco.librealsense.error;
import org.bytedeco.opencv.opencv_core.Mat;
import org.json.JSONException;
import org.json.JSONObject;
import org.kaldi.Assets;
import org.kaldi.Model;
import org.kaldi.SpeechRecognizer;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class Tasks {

    public static class SetupTask extends AsyncTask<String, Void, Exception> {
        static WeakReference<AssistantService> serviceReference;
        static File CascadeFile;

        SetupTask(AssistantService service) {
            this.serviceReference = new WeakReference<>(service);
        }

        //@SuppressLint("WrongThread")
        @SuppressLint("WrongThread")
        @Override
        protected Exception doInBackground(String... params) {
            try {
                Assets assets = new Assets(serviceReference.get());
                File assetDir = assets.syncAssets();
                Log.d("KaldiDemo", "Sync files in the folder " + assetDir.toString());
                if (params.length > 0)
                    Log.i("Params: ", params[0]);
                String modelFolder = params.length > 0 ? "/" + params[0] : "/model-android-en";
                serviceReference.get().model = new Model(assetDir.toString() + modelFolder);
                CascadeFile = null;
                final InputStream is;
                FileOutputStream os;
                try {
                    AssetManager am = this.serviceReference.get().getResources().getAssets();
                    is = am.open("haarcascade_frontalface_alt2.xml");
                    File cascadeDir = this.serviceReference.get().getDir("cascade", Context.MODE_PRIVATE);
                    CascadeFile = new File(cascadeDir, "face_frontal.xml");
                    os = new FileOutputStream(CascadeFile);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    is.close();
                    os.close();
                } catch (IOException e) {
                    Log.e("SetupTask", "face cascade not found");
                }
                new Tasks.ActionTask(serviceReference.get()).execute("");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (RuntimeException e){
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            super.onPostExecute(result);
        }
    }

    public static class RecognizeTask extends AsyncTask<Void, Void, Exception> {
        static WeakReference<AssistantService> serviceReference;
        static RecognizeTask Task;

        RecognizeTask(AssistantService service) {
            serviceReference = new WeakReference<>(service);
            Task = this;
        }

        @Override
        protected Exception doInBackground(Void... params) {
            SpeechRecognizer recognizer = serviceReference.get().recognizer;
            try {
                if (recognizer != null) {
                    recognizer.cancel();
                    recognizer = null;
                } else {
                    try {
                        recognizer = new SpeechRecognizer(serviceReference.get().model);
                        recognizer.addListener(serviceReference.get());
                        recognizer.startListening();
                        serviceReference.get().recognizer = recognizer;
                    } catch (IOException e) {
                        //setErrorState(e.getMessage());
                        if (recognizer != null) {
                            recognizer.cancel();
                            recognizer = null;
                        }
                        throw (e);
                    }
                }
            } catch (IOException e) {
                if (recognizer != null) {
                    recognizer.cancel();
                    recognizer = null;
                }
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            super.onPostExecute(result);
        }
    }
    private static WavFileProperties extractWavProperties(byte[] wavData) {
        if (wavData.length < 44) {
            return null; // WAV header must be at least 44 bytes
        }

        // Extract fields from the WAV header
        int sampleRate = (wavData[24] & 0xFF) | ((wavData[25] & 0xFF) << 8) |
                ((wavData[26] & 0xFF) << 16) | ((wavData[27] & 0xFF) << 24);
        int byteRate = (wavData[28] & 0xFF) | ((wavData[29] & 0xFF) << 8) |
                ((wavData[30] & 0xFF) << 16) | ((wavData[31] & 0xFF) << 24);
        int blockAlign = (wavData[32] & 0xFF) | ((wavData[33] & 0xFF) << 8);
        int bitsPerSample = (wavData[34] & 0xFF) | ((wavData[35] & 0xFF) << 8);

        int channels = (wavData[22] & 0xFF) | ((wavData[23] & 0xFF) << 8);
        int channelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;

        int audioFormat = (bitsPerSample == 8) ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;

        // WAV data offset (after header)
        int dataOffset = 44;
        int dataLength = wavData.length - dataOffset;

        return new WavFileProperties(sampleRate, channelConfig, audioFormat, dataOffset, dataLength, bitsPerSample);
    }

    public static class WavFileProperties {
        int sampleRate;
        int channelConfig;
        int audioFormat;
        int dataOffset;
        int dataLength;

        int bytesPerSample;

        // Constructor now calculates bytesPerFrame
        WavFileProperties(int sampleRate, int channelConfig, int audioFormat, int dataOffset, int dataLength, int bytesPerSample) {
            this.sampleRate = sampleRate;
            this.channelConfig = channelConfig;
            this.audioFormat = audioFormat;
            this.dataOffset = dataOffset;
            this.dataLength = dataLength;
            this.bytesPerSample = bytesPerSample;
        }
    }
    private static final Object playbackLock = new Object();
    private static void playWavAudio(final byte[] wavData) {
        new Thread(() -> {
            synchronized (playbackLock) { // Ensure only one playback at a time
                try {
                    // Extract WAV file properties from the header
                    WavFileProperties wavProperties = extractWavProperties(wavData);

                    if (wavProperties == null) {
                        throw new IllegalArgumentException("Invalid WAV file");
                    }

                    // Create an AudioTrack for playing WAV audio
                    AudioTrack audioTrack = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            wavProperties.sampleRate, // Sample rate
                            wavProperties.channelConfig, // Channel configuration
                            wavProperties.audioFormat, // Audio format (PCM 8-bit or 16-bit)
                            wavProperties.dataLength, // Buffer size (data length in bytes)
                            AudioTrack.MODE_STATIC
                    );

                    // Write WAV data (skipping the header) to the AudioTrack buffer
                    audioTrack.write(wavData, wavProperties.dataOffset, wavProperties.dataLength);

                    // Set the playback listener to detect when playback finishes
                    audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                        @Override
                        public void onMarkerReached(AudioTrack track) {
                            // Marker reached (audio finished)
                            track.release();
                        }

                        @Override
                        public void onPeriodicNotification(AudioTrack track) {
                            // Optionally handle periodic notifications, but not needed here
                        }
                    });

                    // Start playback
                    audioTrack.play();

                    // Calculate the correct position for the notification marker
                    int totalFrames = wavProperties.dataLength / wavProperties.bytesPerSample * 8;

                    // Set the notification marker to the position of the last frame
                    audioTrack.setNotificationMarkerPosition(totalFrames);

                    // Wait for playback to finish without blocking the thread
                    while (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        // Simply wait until playback completes. The callback will handle cleanup.
                        try {
                            Thread.sleep(100); // Avoid high CPU usage during wait
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start(); // Start the background thread
    }


    public static boolean isReconnecting = false;
    public static WebSocket globalWebSocket = null;

    public static String ipv4 = null;

    public static void scheduleReconnect(WeakReference<AssistantService> serviceReference) {
        try {
            if (isReconnecting) {
                scheduleReconnect(serviceReference);
                return ;
            }
            isReconnecting = true;
            Log.d(TAG, "Attempting to reconnect in 2 seconds...");
            serviceReference.get().sendNotification("Websocket", "Reconnecting");

            AsyncServer.getDefault().postDelayed(() -> {
                try {
                    Log.d(TAG, "Reconnecting to WebSocket...");
                    connectToWebSocket(serviceReference);
                }catch(Exception e){
                    e.printStackTrace();
                    scheduleReconnect(serviceReference);
                }

            }, 2000); // Reconnect after 2 seconds */
        }catch(Exception e){
            e.printStackTrace();
            scheduleReconnect(serviceReference);
        }

    }

    public static void SendPreferencesJson(Context context, String... text){
        GetIp(() -> {
            Integer speaker = getPreferenceI(context, "speaker");
            Integer video = getPreferenceI(context, "video");
            String[] model = getPreferenceS(context, "ModelFileName").split("-");
            String lang = model[model.length-1];
            Integer google = getPreferenceI(context, "google");
            JSONObject jobj = new JSONObject();
            try {
                jobj.accumulate("ip", ipv4);
                jobj.accumulate("speaker", speaker);
                jobj.accumulate("video", video);
                jobj.accumulate("lang", lang);
                jobj.accumulate("google", google);
                jobj.accumulate("text", text.length > 0 ? text[0] : "");
                String json = jobj.toString();
                if(globalWebSocket != null)
                    globalWebSocket.send(json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    public static void connectToWebSocket(WeakReference<AssistantService> serviceReference){
        try {
            //AsyncHttpClient.getDefaultInstance().getServer().stop();
            String jarvisUrl = getPreferenceS(serviceReference.get().getContext(),"serverip");
            if(jarvisUrl.isEmpty())
                jarvisUrl = "192.168.1.104:80";

            String credentials = "user:testing";
            String encodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
            String authUrl = "?auth=" + encodedCredentials;

            AsyncHttpClient.getDefaultInstance().websocket("ws://"+jarvisUrl, "my-protocol", new AsyncHttpClient.WebSocketConnectCallback() {
                @Override
                public void onCompleted(Exception ex, WebSocket webSocket) {
                    isReconnecting = false;
                    if(webSocket != null){
                        webSocket.setDataCallback((e, b) -> {});
                        webSocket.setClosedCallback((h) -> { scheduleReconnect(serviceReference);});
                        webSocket.setEndCallback((h) -> { scheduleReconnect(serviceReference);});
                    }
                    if (ex != null) {
                        ex.printStackTrace();
                        scheduleReconnect(serviceReference);
                        return;
                    }
                    globalWebSocket = null;
                    globalWebSocket = webSocket;
                    webSocket.send("Android Connected");
                    serviceReference.get().sendNotification("Websocket", "Connected");

                    VideoPopup videoPopup = new VideoPopup(serviceReference.get().getContext());

                    //webSocket.send(new byte[10]);

                    GetIp();
                    SendPreferencesJson(serviceReference.get().getContext());

                    webSocket.setStringCallback(new WebSocket.StringCallback() {
                        public void onStringAvailable(String s) {
                            System.out.println("I got a string: " + s);
                            if(s.toLowerCase().contains("text:")){
                                if(getPreferenceI(serviceReference.get().getContext(), "local") == 1)
                                    serviceReference.get().tts.speak(s.replace("text:", ""), TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                                else {
                                    Pair<String, Boolean> result = new Pair<>(s.replace("text:", ""), true);
                                    ActionTask.propertyChangeSupport.firePropertyChange("partialResult",null, result);
                                }
                            }
                        }
                    });
                    webSocket.setDataCallback(new DataCallback() {
                        public void onDataAvailable(DataEmitter emitter, ByteBufferList byteBufferList) {
                            System.out.println("I got some bytes!");
                            // Handle binary data (WAV file)
                            byte[] data = new byte[byteBufferList.remaining()];
                            byteBufferList.get(data);
                            // note that this data has been read
                            byteBufferList.recycle();
                            if(FileTypeChecker.isMp4(data)){
                                videoPopup.showVideoPopup(data);
                            }
                            else if(FileTypeChecker.isWav(data)){
                                playWavAudio(data);
                            }
                        }
                    });
                    // Handle WebSocket closure
                    webSocket.setClosedCallback(new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception ex) {
                            if (ex != null) {
                                Log.e(TAG, "WebSocket closed with error", ex);
                            } else {
                                Log.d(TAG, "WebSocket closed successfully");
                            }
                            scheduleReconnect(serviceReference);
                        }
                    });

                    // Handle WebSocket errors
                    webSocket.setEndCallback(new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception ex) {
                            if (ex != null) {
                                Log.e(TAG, "WebSocket error", ex);
                            }
                            scheduleReconnect(serviceReference);
                        }
                    });
                }
            });

        }catch(Exception e){
            e.printStackTrace();
            scheduleReconnect(serviceReference);
        }

    }

    public static void sendWebsocket(String ip, String protocol, String msg) {
        AsyncHttpClient.getDefaultInstance().websocket("ws://" + ip, protocol, (ex, webSocket) -> {
            if (ex != null) {
                ex.printStackTrace();
                return;
            }
            webSocket.setStringCallback(s -> Log.i("Websocket", "data: " + s));
            webSocket.send(msg);
        });
    }

    public static void sendHTTP(String url, Function<String, String> fn) {
        AsyncHttpClient.getDefaultInstance().execute(url, (ex, response) -> {
            Log.i("HTTP ERROR", ex.getMessage());
            Log.i("HTTP RESPONSE: ", response.message());
            if(fn != null)
                fn.apply(response.message());
        });
    }

    public static void GetIp(Runnable... runnable){
        String url = "https://api.ipify.org/?format=json";

        HttpRequestHelper.sendHttpGetRequest(url, new HttpRequestHelper.HttpResponseCallback() {
            @Override
            public void onResponse(String result) throws JSONException {
                // Handle the successful response
                try {
                    System.out.println("Response: " + result);
                    ipv4 = new JSONObject(result).getString("ip");
                    if(runnable.length > 0)
                        runnable[0].run();
                }catch(Exception e){
                    e.printStackTrace();
                }

            }

            @Override
            public void onError(String error) {
                // Handle the error response
                System.out.println("Error: " + error);
            }
        });
    }

    public static class ActionTask extends AsyncTask<String, Void, String> {
        static WeakReference<AssistantService> serviceReference;
        static ActionTask Task;
        public AsyncHttpServer server;
        public List<WebSocket> _sockets = new ArrayList<WebSocket>();
        public static PropertyChangeSupport propertyChangeSupport;
        Beeper beeper;
        private StringBuilder LLMQueue = new StringBuilder();  // Accumulates incomplete sentences
        private int local;
        public ImagePopup imagePopup;
        public PropertyChangeListener listener = evt -> {
            Pair<String, Boolean> result = (Pair<String, Boolean>) evt.getNewValue();
            String partialText = result.first;
            boolean isDone = result.second;

            System.out.println("Received partial result: " + partialText + ", done: " + isDone);

            // Append the latest partial text to the queue
            LLMQueue.append(partialText);

            // Process complete sentences from the accumulated text
            String accumulatedText = LLMQueue.toString();
            String[] sentences = accumulatedText.split("(?<=\\.)");

            // Clear the LLMQueue and rebuild it with any remaining (incomplete) sentence
            LLMQueue.setLength(0);

            for (int i = 0; i < sentences.length; i++) {
                String sentence = sentences[i].trim();

                // If the sentence is complete (ends with a period), speak it
                if (sentence.endsWith(".")) {
                    serviceReference.get().sendNotification("LLM", sentence);
                    serviceReference.get().tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                } else {
                    // Incomplete sentence, re-add it to the queue for the next partial result
                    LLMQueue.append(sentence);
                }
            }

            // If this is the final result and there's remaining text in the queue, speak it
            if (isDone && LLMQueue.length() > 0) {
                String finalSentence = LLMQueue.toString().trim();
                serviceReference.get().sendNotification("LLM", finalSentence);
                serviceReference.get().tts.speak(finalSentence, TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                LLMQueue.setLength(0);  // Clear the queue after processing the final text
            }
        };

        ActionTask(AssistantService service) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
            try{
                serviceReference = new WeakReference<>(service);
                Task = this;
                //beeper = new Beeper();
                propertyChangeSupport = new PropertyChangeSupport(this);
                local = getPreferenceI(serviceReference.get().getContext(), "local");
                if(local == 1 )
                    LLM.getInstance(serviceReference.get().getContext(), null).addPartialResultListener(listener);

                if(server != null)
                    server.stop();

                server = new AsyncHttpServer();
                server.listen(AsyncServer.getDefault(), 8080);

                if(local == 0 )
                    connectToWebSocket(serviceReference);

                imagePopup = new ImagePopup(serviceReference.get().getContext());
                GetIp();
                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                        try {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                serviceReference.get().Toast(paramThrowable.toString());
                            });
                            Thread.yield();
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                });
                server.websocket("/speak", new AsyncHttpServer.WebSocketRequestCallback() {
                    @Override
                    public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
                        _sockets.add(webSocket);

                        //Use this to clean up any references to your websocket
                        webSocket.setClosedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                try {
                                    if (ex != null)
                                        Log.e("WebSocket", "An error occurred", ex);
                                } finally {
                                    _sockets.remove(webSocket);
                                }
                            }
                        });

                        webSocket.setStringCallback(new WebSocket.StringCallback() {
                            @Override
                            public void onStringAvailable(String s) {
                                serviceReference.get().tts.speak(s, TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                                webSocket.send("sent to server: "+s);
                                Log.i("websocket", "str from websocket client: "+s);
                            }
                        });

                    }
                });
                server.websocket("/sound", new AsyncHttpServer.WebSocketRequestCallback() {
                    @Override
                    public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
                        _sockets.add(webSocket);

                        //Use this to clean up any references to your websocket
                        webSocket.setClosedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception ex) {
                                try {
                                    if (ex != null)
                                        Log.e("WebSocket", "An error occurred", ex);
                                } finally {
                                    _sockets.remove(webSocket);
                                }
                            }
                        });
                        /*webSocket.setDataCallback(new DataCallback(){

                            @Override
                            public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                                // Handle binary data (WAV file)
                                byte[] data = new byte[byteBufferList.remaining()];
                                byteBufferList.get(data);

                                playWavAudio(data);
                            }
                        });*/
                        webSocket.setStringCallback(new WebSocket.StringCallback() {
                            @RequiresApi(api = Build.VERSION_CODES.M)
                            @Override
                            public void onStringAvailable(String s) {
                            /*
                            serviceReference.get().mediaPlayer.stop();
                            serviceReference.get().mediaPlayer.reset();
                            serviceReference.get().mediaPlayer.release();
                            serviceReference.get().mediaPlayer = MediaPlayer.create(serviceReference.get(), R.raw.sonar);
                            */
                                if(s.equals("ok")) {
                                    beeper.stopBeeper(0, true);
                                    return;
                                }
                                float speed = s.equals("red")
                                        ? 6.0f
                                        : s.equals("orange")
                                        ? 4.0f
                                        : s.equals("blue")
                                        ? 2.0f
                                        : s.equals("green")
                                        ? 1.0f
                                        : 1.0f;
                            /*
                            serviceReference.get().mediaPlayer.setPlaybackParams(serviceReference.get().mediaPlayer.getPlaybackParams().setSpeed(speed));
                            serviceReference.get().mediaPlayer.setLooping(true);
                            serviceReference.get().mediaPlayer.start();
                            */
                                if (s.equals("red")) {
                                    beeper.beepFast();
                                } else {
                                    if (s.equals("orange")) {
                                        beeper.beepHigh();
                                    } else {
                                        if (s.equals("blue")) {
                                            beeper.beepMedium();
                                        } else {
                                            if (s.equals("green")) {
                                                beeper.beepSlow();
                                            } else {
                                                beeper.beepSlow();
                                            }
                                        }
                                    }
                                }
                                webSocket.send("sent to server: "+s);
                                Log.i("websocket", "str from websocket client: "+s);
                            }
                        });

                    }
                });

            }catch(Exception e){
                Log.e("ActionTask", e.getMessage());
                e.printStackTrace();
                //serviceReference.get().Toast("exception Actiontask Ctor: "+e.getMessage());
            }catch(Throwable t){
                t.printStackTrace();
            }

        }

        //@SuppressLint("WrongThread")
        @Override
        protected String doInBackground(String... str) {
            try {
                String text = str[0];
                text = text.toLowerCase();
                Log.i("ActionTask", "data: " + text);

               /* if (text.toLowerCase(Locale.ROOT).contains("laura")) {
                    serviceReference.get().tts.speak("Bonjour Laura, je m'appel "+serviceReference.get().keyword+ "et je suis fou amoureux de toi, tu es la plus belle femme du monde, voudrais tu devenir ma femme ?", TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                }else */
                if(!text.isEmpty())
                    serviceReference.get().sendNotification("Recognized", text);

                if(local == 0 && !text.isEmpty()){

                    if (text.contains("see") || text.contains("vois")) {
                        Camera cam = new Camera(serviceReference.get());
                        cam.startBackgroundThread();
                        cam.openCamera(text.contains("front") || text.contains("devant") ? 1 : 0);
                        new android.os.Handler().postDelayed(
                                () -> {
                                    String picturePath = cam.takePicture(serviceReference);
                                    new android.os.Handler().postDelayed(
                                            () -> {
                                                try {
                                                    byte[] byteArray = FileUtils.getFileBytes(picturePath);
                                                    imagePopup.showImagePopup(byteArray);
                                                    //String base64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
                                                    SendPreferencesJson(serviceReference.get().getContext());
                                                    if(globalWebSocket != null){
                                                        globalWebSocket.send(ImageUtils.resizeAndCompressImage(byteArray,50));
                                                    }
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            },
                                            1000);
                                },
                                2000);

                    }
                    else{
                        SendPreferencesJson(serviceReference.get().getContext(), text);
                    }

                }else if (text.isEmpty()) {
                        //serviceReference.get().tts.speak("Hello Boss !"+serviceReference.get().keyword, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(1));
                        serviceReference.get().tts.speak("Hello Boss !", TextToSpeech.QUEUE_FLUSH, null, String.valueOf(1));
                }
                else if (text.contains("hand") || text.contains("main") || text.contains("robot")) {
                    String txt = text.contains("open") || text.contains("clos") || text.contains("ouvr") || text.contains("ferm") ? "value:1" : "mode:" + replaceAny(text, new ArrayList<String>() {
                        {
                            add("main");
                            add("hand");
                            add("robot");
                            add("mode");
                        }
                    }).trim();
                    String ip = getPreferenceS(serviceReference.get().getContext(), "handip");
                    sendWebsocket(ip, "arduino", txt);
                    serviceReference.get().tts.speak("Okay Boss !", TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                } else if (text.contains("light") || text.contains("lumière")) {
                    String condition = text.contains("on") || text.contains("allum") ? "on" : "off";
                    sendHTTP("http://192.168.43.86/" + condition, null);
                    serviceReference.get().tts.speak("Okay Boss ! " + condition, TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                } else if (text.contains("stop") || text.contains("terminate")) {
                    serviceReference.get().getApplicationContext().stopService(new Intent(serviceReference.get().getApplicationContext(), AssistantService.class));
                    serviceReference.get().getApplicationContext().stopService(new Intent(serviceReference.get().getApplicationContext(), FloatingWindowService.class));
                    System.exit(0);
                } else if (text.contains("sms")) {
                    SmsManager sms = SmsManager.getDefault();
                    //sms.downloadMultimediaMessage();
                    //sms.sendTextMessage(srcNumber, null, message, null, null);
                } else if (text.contains("photo") || text.contains("picture")) {
                    serviceReference.get().takeFrontPicture();
                } else if (text.contains("recognition") || text.contains("reconnaissance") || text.contains("facial")) {
                    //new CameraTask(serviceReference).execute();
                    Camera cam = new Camera(serviceReference.get());
                    cam.startBackgroundThread();
                    cam.openCamera(1);
                    new android.os.Handler().postDelayed(
                            () -> {
                                String picturePath = cam.takePicture(serviceReference, true);
                                new android.os.Handler().postDelayed(
                                        () -> {
                                            try {
                                                byte[] byteArray = FileUtils.getFileBytes(picturePath);
                                                imagePopup.showImagePopup(byteArray);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        },
                                        1000);
                            },
                            2000);

                } else if (text.contains("video") || text.contains("film") || text.contains("record")) {
                    serviceReference.get().takeVideo();
                }
               /* else if (text.contains("app")) {
                    restartMainActivity(serviceReference.get().getContext());
                } */
                else if (text.contains("time") || text.contains("heure")) {
                    SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
                    Date date = new Date();
                    String formatted = formatter.format(date);
                    System.out.println(formatted);
                    String[] tab = formatted.split(":", 2);
                    String out = tab[0] + "H, " + tab[1] + "Minutes";
                    System.out.println(out);
                    serviceReference.get().tts.speak(out, TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                } else if (containsAny(text, new ArrayList<String>() {
                    {
                        add("repeat");
                        add("say");
                        add("répète");
                        add("repeter");
                        add("repete");
                    }
                })) {
                    String newText = replaceAny(text, new ArrayList<String>() {
                        {
                            add("repeat");
                            add("say");
                            add("répète");
                            add("repeter");
                            add("repete");
                        }
                    });
                    serviceReference.get().tts.speak(newText, TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                } else if (text.contains("avion") || text.contains("plane")) {
                    String condition = text.contains(" on ") || text.contains("allum") || text.contains("met") ? "on" : "off";
                    Settings.Global.putInt(serviceReference.get().getContentResolver(), "airplane_mode_on", condition.equals("on") ? 1 : 0);
                }else if (text.contains("bluetooth")) {
                    String condition = text.contains("on") || text.contains("allum") || text.contains("met") ? "on" : "off";
                    Settings.Global.putInt(serviceReference.get().getContentResolver(), "BLUETOOTH_ON", condition.equals("on") ? 1 : 0);
                }else if (text.contains("data") && text.contains("roaming")) {
                    String condition = text.contains("on") || text.contains("allum") || text.contains("met") ? "on" : "off";
                    Settings.Global.putInt(serviceReference.get().getContentResolver(), Settings.Global.DATA_ROAMING, condition.equals("on") ? 1 : 0);
                } else if (text.contains("data") && text.contains("mobile")) {
                    String condition = text.contains(" on ") || text.contains("allum") || text.contains("met") ? "on" : "off";
                    Settings.Global.putInt(serviceReference.get().getContentResolver(), Settings.Global.RADIO_CELL, condition.equals("on") ? 1 : 0);
/*                    final ConnectivityManager conman = (ConnectivityManager) serviceReference.get().getSystemService(Context.CONNECTIVITY_SERVICE);
                    final Class conmanClass = Class.forName(conman.getClass().getName());
                    final Field connectivityManagerField = conmanClass.getDeclaredField("mService");
                    connectivityManagerField.setAccessible(true);
                    final Object connectivityManager = connectivityManagerField.get(conman);
                    final Class connectivityManagerClass = Class.forName(connectivityManager.getClass().getName());
                    final Method setMobileDataEnabledMethod = connectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                    setMobileDataEnabledMethod.setAccessible(true);
                    setMobileDataEnabledMethod.invoke(connectivityManager, condition.equals("on")); // pass true or false  */
                    //////////////////////
//                    final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
//                    final Class conmanClass = Class.forName(conman.getClass().getName());
//                    final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
//                    iConnectivityManagerField.setAccessible(true);
//                    final Object iConnectivityManager = iConnectivityManagerField.get(conman);
//                    final Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
//                    final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
//                    setMobileDataEnabledMethod.setAccessible(true);
//
////                    setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
//                    try {
//                        final ConnectivityManager conman = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
//                        final Class<?> conmanClass = Class.forName(conman.getClass().getName());
//                        final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
//                        iConnectivityManagerField.setAccessible(true);
//                        final Object iConnectivityManager = iConnectivityManagerField.get(conman);
//                        final Class<?> iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
//                        final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
//                        setMobileDataEnabledMethod.setAccessible(true);
//                        setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
//                    }
//                    catch (Exception e)
//                    {
//                        e.printStackTrace();
//                    }
                } else if (text.toLowerCase(Locale.ROOT).contains("wifi")) {
                    String condition = text.contains("on") || text.contains("allum") || text.contains("met") ? "on" : "off";
                    Settings.Global.putInt(serviceReference.get().getContentResolver(), Settings.Global.WIFI_ON, condition.equals("on") ? 1 : 0);
                }else if (!text.isEmpty()) {
                    //String prompt = "Tu es un expert francais sur tous les sujets et tu vas repondre a toutes mes demandes par des phrases simples et très courtes, maintenant tu vas simplement repondre normalement a ça : ";
                    //llm.generateResponse(prompt+text);
                    String prompt = "<start_of_turn>model\nYou Speak french and answer in french thanks. Tu parles francais et reponds en francais merci.<end_of_turn><start_of_turn>user\n"+text+"<end_of_turn>";
                    LLM.getInstance(serviceReference.get().getContext(), null).generateResponse(text);
                }

                /*try{
                    // To Write
                    Settings.Global.putString(getContentResolver(), "airplane_mode_on", "1");
                    Settings.Global.WIFI_ON;
                    Settings.Global.BLUETOOTH_ON;
                    Settings.Global.MODE_RINGER;
                    Settings.Global.DATA_ROAMING;
                    //Settings.Secure.putInt(getContentResolver(), "airplane_mode_on", 1);
                    // To Read
                    String result = Settings.Global.getString(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON);
                    Toast.makeText(this, "result:"+result, Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Log.e("Exception airplane: ", e.getMessage());
                }*/
            } catch (Exception e) {
                Log.i("exception Actiontask", e.getMessage());
                serviceReference.get().Toast("exception Actiontask: "+e.getMessage());
            }
            return str[0];
        }
    }

    public static class FaceDetectTask extends AsyncTask<File, Void, ArrayList<Mat>> {
        @Override
        protected ArrayList<Mat> doInBackground(File... file) {
            try {
                return OpenCVFaceRecognizer.GetInstance().DetectFaces(file[0].getAbsolutePath());
            } catch (Exception e) {

            }
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<Mat> result) {
            super.onPostExecute(result);
        }
    }

    public static class TrainTask extends AsyncTask<File, Void, Exception> {

        @Override
        protected Exception doInBackground(File... file) {
            try {
                OpenCVFaceRecognizer.GetInstance().Train(file[0]);
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            super.onPostExecute(result);
        }
    }

    public static class FaceRecognizerTask extends AsyncTask<Mat, Void, String> {

        @Override
        protected String doInBackground(Mat... mat) {
            try {
                return OpenCVFaceRecognizer.GetInstance().Recognize(mat[0]);
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }

    public static class BluetoothTask extends AsyncTask<String, Void, String> {
        static WeakReference<AssistantService> serviceReference;
        static BluetoothTask Task;

        BluetoothTask(AssistantService service) {
            serviceReference = new WeakReference<>(service);
        }
        @Override
        protected String doInBackground(String... deviceName) {
            /*//MessageHandler is call when bytes are read from the serial input
            BluetoothSerial bluetoothSerial = new BluetoothSerial(serviceReference.get(), new BluetoothSerial.MessageHandler() {
                @Override
                public int read(int bufferSize, byte[] buffer) {
                    //return doRead(bufferSize, buffer);
                    String msg = (new String(buffer)).split("\r\n")[0];
                    System.out.println(msg);
                    return bufferSize;
                }
            }, "ESP32");*/
            //Fired when connection is established and also fired when onResume is called if a connection is already established.
            LocalBroadcastManager.getInstance(serviceReference.get()).registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    return;
                }
            }, new IntentFilter(BluetoothSerial.BLUETOOTH_CONNECTED));
            //Fired when the connection is lost
            LocalBroadcastManager.getInstance(serviceReference.get()).registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    return;
                }
            }, new IntentFilter(BluetoothSerial.BLUETOOTH_DISCONNECTED));
            //Fired when connection can not be established, after 30 attempts.
            LocalBroadcastManager.getInstance(serviceReference.get()).registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    return;
                }
            }, new IntentFilter(BluetoothSerial.BLUETOOTH_FAILED));
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }

    public static void restartMainActivity(Context context) {
        Intent i = new Intent();
        i.setAction(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.setComponent(new ComponentName(context.getPackageName(), MainActivity.class.getName()));
        context.startActivity(i);
    }

    public static boolean isMyServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static String getPreferenceS(Context context, String key){
        SharedPreferences sharedPref = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
        return sharedPref.getString(key,"MainActivity");
    }

    public static int getPreferenceI(Context context,String key){
        SharedPreferences sharedPref = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
        return sharedPref.getInt(key, 0);
    }

    public static Activity getActivity() throws ClassNotFoundException, NoSuchMethodException, NoSuchFieldException, InvocationTargetException, IllegalAccessException {
        Class activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
        activitiesField.setAccessible(true);

        Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(activityThread);
        if (activities == null)
            return null;

        for (Object activityRecord : activities.values()) {
            Class activityRecordClass = activityRecord.getClass();
            Field pausedField = activityRecordClass.getDeclaredField("paused");
            pausedField.setAccessible(true);
            if (!pausedField.getBoolean(activityRecord)) {
                Field activityField = activityRecordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(activityRecord);
                return activity;
            }
        }

        return null;
    }

    public static boolean containsAny(String text, ArrayList<String> words) {
        for (String word : words) {
            if (text.toLowerCase().contains(word.toLowerCase()))
                return true;
        }
        return false;
    }

    public static String replaceAny(String text, ArrayList<String> words) {
        String output = text.toLowerCase();
        for (String word : words) {
            output = output.replace(word.toLowerCase(), "");
        }
        return output;
    }

}