package com.assistant.main;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.assistant.main.helpers.Beeper;

import org.json.JSONException;
import org.json.JSONObject;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechRecognizer;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import ai.picovoice.porcupinemanager.PorcupineManager;
import ai.picovoice.porcupinemanager.PorcupineManagerException;

public class AssistantService extends android.app.Service implements RecognitionListener {
    private static final String CHANNEL_ID = "AssistantServiceChannel";

    private PorcupineManager porcupineManager;
    public SpeechRecognizer recognizer;
    public Model model;
    public MediaPlayer mediaPlayer;
    public TextToSpeech tts;
    public com.assistant.main.camera.Camera Camera;
    public String keyword = null;
    public String modelFileName = null;
    public Beeper beeper = null;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_HIGH);

            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);
        }
    }

    private TextToSpeech getTTS(){
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onInit(int status) {
                System.out.println("tts status: "+status);
                String mfn = modelFileName;
                Locale enOrFr = mfn != null && mfn.toLowerCase().contains("en") ? Locale.UK : Locale.FRENCH;
                String language = enOrFr.getLanguage().toLowerCase();
                tts.setLanguage(enOrFr);
                tts.setPitch((float) 0.7);
                tts.setSpeechRate((float) 1);
                Set<Voice> voices = tts.getVoices();
                voices.removeIf(voice -> {
                    boolean val = false;
                    //val = !voice.getName().contains(language.contains("en") ? "en-gb" : "fr-fr");
                    val = !voice.getLocale().getCountry().contains(language.contains("en") ? "GB" : "FR");
                    val = val || !voice.getName().contains(language.contains("en") ? "gbb" : "frb");
                    return val;
                });
                tts.setVoice((Voice)voices.toArray()[voices.toArray().length - 1]);
            }
        });
        return tts;
    }
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(1234, new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Assistant Service")
                    .setContentText("Waiting for command")
                    .setSmallIcon(R.drawable.ic_stat_mic)
                    .setContentIntent(PendingIntent.getActivity(
                            this,
                            0,
                            new Intent(this, MainActivity.class),
                            0))
                    .build());
            createNotificationChannel();
            mediaPlayer = MediaPlayer.create(this, R.raw.notification);
            beeper = new Beeper();
            String modelFilePath = new File(this.getFilesDir(), "porcupine_params.pv").getAbsolutePath();

            String kwd = "jarvis_android.ppn";
            try{
                 kwd = intent.getStringExtra("keywordFileName");
            }catch(Exception e){}
            keyword = kwd.split("_")[0];
            String keywordFilePath = new File(this.getFilesDir(), kwd).getAbsolutePath();
            Log.i("Extra", kwd);
            Log.i("keywordFilePath", keywordFilePath);
            String mfn = null;
            try{
                mfn = intent.getStringExtra("modelFileName");
                modelFileName = mfn;
                if(mfn != null)
                    Log.i("ModelFileName", mfn);
            }catch(Exception e){
            }
            if(mfn != null){
                new Tasks.SetupTask(this).execute(mfn);
            }
            else
                new Tasks.SetupTask(this).execute("");
            getTTS();
            /*int enrollment = getPreferenceI("enroll");
            if(enrollment == 0){
                Camera = new Camera(this);
                Camera.startBackgroundThread();
                Camera.openCamera();
            }*/
            try {
                porcupineManager = new PorcupineManager(
                        modelFilePath,
                        keywordFilePath,
                        0.7f,
                        (keywordIndex) -> {

                            new Tasks.RecognizeTask(this).execute();

                            if(!mediaPlayer.isPlaying())
                               mediaPlayer.start();
                            if(Settings.canDrawOverlays(getApplicationContext())){
                                if(!Tasks.isMyServiceRunning(getContext(), FloatingWindowService.class))
                                    startService(new Intent(getApplicationContext(), FloatingWindowService.class));
                            }
                        });

                porcupineManager.start();

                //ConnectivityViewModel vm = new ConnectivityViewModel(getApplication());

              /*  vm.getConnected().observe((LifecycleOwner) this, connected -> {
                    serviceReference.get().sendNotification("Connectivity", "available");
                });*/

                //MessageHandler is call when bytes are read from the serial input
                BluetoothSerial bluetoothSerial = new BluetoothSerial(this, new BluetoothSerial.MessageHandler() {
                    @Override
                    public int read(int bufferSize, byte[] buffer) {
                        //return doRead(bufferSize, buffer);
                        String s = (new String(buffer)).split("\r\n")[0];
                        System.out.println(s);
                        Log.i("bluetooth", "str from bluetooth serial: "+s);

                        if(s.equals("ok")) {
                            beeper.stopBeeper(0, true);
                            return bufferSize;
                        }
                        if (s.equals("red")) {
                            beeper.beepFast();
                        } else if (s.equals("orange")) {
                            beeper.beepHigh();
                        } else if (s.equals("blue")) {
                            beeper.beepMedium();
                        } else if (s.equals("green")) {
                            beeper.beepSlow();
                        } else {
                            //beeper.beepSlow();
                            tts.speak(s, TextToSpeech.QUEUE_ADD, null, String.valueOf(1));
                        }
                        return bufferSize;
                    }
                }, "ESP32");


                //Fired when connection is established and also fired when onResume is called if a connection is already established.
                LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Toast("BLUETOOTH_CONNECTED");
                        return;
                    }
                }, new IntentFilter(BluetoothSerial.BLUETOOTH_CONNECTED));
                //Fired when the connection is lost
                LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Toast("BLUETOOTH_DISCONNECTED");
                        return;
                    }
                }, new IntentFilter(BluetoothSerial.BLUETOOTH_DISCONNECTED));
                //Fired when connection can not be established, after 30 attempts.
                LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Toast("BLUETOOTH_FAILED");
                        return;
                    }
                }, new IntentFilter(BluetoothSerial.BLUETOOTH_FAILED));
                SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
                Integer bluetooth = sharedPref.getInt("SetBluetooth", 0);
                if(bluetooth == 1)
                    bluetoothSerial.connect();

                tts.speak("", TextToSpeech.QUEUE_FLUSH, null, String.valueOf(1));

            } catch (PorcupineManagerException e) {
                Log.e("ASSISTANT_SERVICE", e.toString());
            }catch (Exception e){
                Log.e("ASSISTANT_SERVICE_CAMERA", e.toString());
            }
            return super.onStartCommand(intent, flags, startId);

        }catch (Exception e){
            Log.e("ASSISTANT_SERVICE_OTHER", e.toString());
        }
        return 0;
        //return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i("AssisstantService: ", "Destroyed");
        try {
            if(porcupineManager != null ) porcupineManager.stop();
            if(tts != null )tts.shutdown();
            if(mediaPlayer != null )mediaPlayer.release();
            if(recognizer != null )recognizer.shutdown();
            if(Camera != null )Camera.closeCamera();
            //if(server != null)server.stop();
        } catch (PorcupineManagerException e) {
            Log.e("ASSISTANT_SERVICE", e.toString());
        }

        super.onDestroy();
    }

    @Override
    public void onPartialResult(String s) {
        System.out.println("Partial: "+s);
    }

    @Override
    public void onError(Exception e) {
        System.out.println(e.getMessage());
    }

    @Override
    public void onTimeout() { }

    @Override
    public void onResult(String s) {
        System.out.println("Final: "+s);
        JSONObject reader = null;
        try {
            reader = new JSONObject(s);
            String text = reader.getString("text");
            Tasks.ActionTask.Task.doInBackground(text);
            //Tasks.ActionTask.Task.execute(text); // does not work
        } catch (JSONException e) {
            stopService(new Intent(getContext(), FloatingWindowService.class));
            e.printStackTrace();
        }
       /* catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }*/
        if(recognizer != null){
            recognizer.cancel();
            recognizer = null;
        }
        stopService(new Intent(getContext(), FloatingWindowService.class));
    }

    public void sendNotification(String title, String text){
        Intent newIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                newIntent,
                0);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_mic)
                .setContentIntent(contentIntent)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.notify(1234, n);
    }

    public Context getContext(){
        return getApplicationContext();
    }

    String currentPhotoPath;

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File directory = new File(storageDir+File.separator+"photos");
        directory.mkdir();
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                directory      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(currentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    private void galleryAddPic(Uri contentUri) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    public void  takeFrontPicture(){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra("android.intent.extra.quickCapture",true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                getContext().startActivity(intent);
                galleryAddPic(photoURI);
            }
        }
    }

    public void takeVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) != null) {
            getContext().startActivity(intent);
        }
    }

    private int getPreferenceI(String key){
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("", MODE_PRIVATE);
        return sharedPref.getInt(key, 0);
    }

    public void Toast(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
