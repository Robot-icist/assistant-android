package com.assistant.main;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.json.JSONException;
import org.json.JSONObject;
import org.kaldi.Assets;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechRecognizer;
import org.kaldi.Vosk;
import com.assistant.main.R;
import com.assistant.main.helpers.Cache;
import com.assistant.main.llm.LLM;
import com.google.mediapipe.tasks.core.OutputHandler;

import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    static {
        System.loadLibrary("kaldi_jni");
        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");
    }

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC  = 4;

    public Model model;
    private SpeechRecognizer recognizer;
    TextView resultView;

    public Spinner KeywordSpinner;
    public Spinner ModelSpinner;

    public Spinner LlmModelSpinner;

    public String KeywordFileName;
    public String ModelFileName;

    public String LlmModelFileName;

    private static int[] KEYWORD_FILE_RESOURCE_IDS = {
            R.raw.americano_android, R.raw.blueberry_android, R.raw.bumblebee_android, R.raw.grapefruit_android,
            R.raw.grasshopper_android, R.raw.picovoice_android, R.raw.porcupine_android, R.raw.terminator_android,
            R.raw.alexa_android, R.raw.computer_android, R.raw.hey_google_android, R.raw.hey_siri_android,
            R.raw.ok_google_android,R.raw.jarvis_android
    };

    //CAMERA PART
    private static final String TAG = "AndroidCameraApi";
    private Button takePictureButton;
    private Button testButton;
    private Button trainButton;

    private Button enterButton;
    private ProgressBar loader;

    private Button killButton;
    private TextureView textureView;
    private EditText enrollName;
    private CheckBox enrollCb;
    private CheckBox bluetoothCb;
    private CheckBox localCb;
    private CheckBox videoCb;
    private CheckBox googleCb;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 270);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 90);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    public int setEnroll = -1;
    public String currentPhotoPath;

    public EditText handip;
    public EditText serverip;
    public NumberPicker speaker;
    public EditText text;

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void onCreate(Bundle state) {
        try {

            super.onCreate(state);

        /* //delete all user data
        if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
            ((ActivityManager) getApplicationContext().getSystemService(ACTIVITY_SERVICE))
                    .clearApplicationUserData();
            return;
        }*/

        /* // Delete local cache dir (ignoring any errors):
        FileUtils.deleteQuietly(getApplicationContext().getCacheDir());

        Cache.RemoveCache(getApplicationContext());
        Cache.ClearCache(getApplicationContext());
        Cache.DeleteCache(getApplicationContext());*/

            setContentView(R.layout.main);

            // Setup layout
            resultView = findViewById(R.id.result_text);
            setUiState(STATE_START);

            findViewById(R.id.recognize_mic).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //LLM.getInstance(getApplicationContext()).removePartialResultListener();
                    //LLM.getInstance(getApplicationContext()).stop();
                    recognizeMicrophone();
                }
            });

            findViewById(R.id.reload).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //LLM.getInstance(getApplicationContext()).removePartialResultListener();
                    if(getPreferenceI("local") == 1)LLM.getInstance(getApplicationContext(), null).stop();
                    getWakeWordAndStartService(true);
                    finish();
                }
            });

            findViewById(R.id.kill).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    getApplicationContext().stopService(new Intent(getApplicationContext(), AssistantService.class));
                    getApplicationContext().stopService(new Intent(getApplicationContext(), FloatingWindowService.class));
                    System.exit(0);
                }
            });

            findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Tasks.SendPreferencesJson(getApplicationContext(),"stop");
                    Tasks.RemoveAudioTracks();
                    Tasks.StaticVideoPopup.videoQueue.removeAll(Tasks.StaticVideoPopup.videoQueue);
                    Tasks.StaticVideoPopup.dismissPopup();
                    if(Tasks.AudioTracks.size() == 0)
                        findViewById(R.id.stop).setVisibility(View.GONE);
                }
            });

            ArrayList<String> permissions = new ArrayList<String>();
            permissions.add(Manifest.permission.INTERNET);
            permissions.add(Manifest.permission.CHANGE_NETWORK_STATE);
            permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
            permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
            permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.FOREGROUND_SERVICE);
            permissions.add(Manifest.permission.RECORD_AUDIO);
            permissions.add(Manifest.permission.SEND_SMS);
            permissions.add(Manifest.permission.RECEIVE_SMS);
            permissions.add(Manifest.permission.READ_SMS);
            //permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);
            //permissions.add(Manifest.permission.WRITE_SYNC_SETTINGS);
            permissions.add(Manifest.permission.READ_SYNC_SETTINGS);
            permissions.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
            permissions.add(Manifest.permission.CAMERA);
            permissions.add(Manifest.permission.READ_CONTACTS);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            //permissions.add(Manifest.permission.WRITE_SECURE_SETTINGS);//do not ask or crash
            //permissions.add(Manifest.permission.WRITE_SETTINGS);

            CheckPermissions(permissions);

            try {
                copyResourceFile(R.raw.porcupine_params, "porcupine_params.pv");

                Resources resources = getResources();
                for (int keywordFileResourceID : KEYWORD_FILE_RESOURCE_IDS) {
                    copyResourceFile(keywordFileResourceID, resources.getResourceEntryName(keywordFileResourceID) + ".ppn");
                }
            } catch (IOException e) {
                Toast.makeText(this, "Failed to copy resource files.", Toast.LENGTH_SHORT).show();
            }

            PopulateSpinners();
            getWakeWordAndStartService(false);
        /*checkDrawOverlayPermission();
        if(Settings.canDrawOverlays(this))
            if(!Tasks.isMyServiceRunning(getApplicationContext(),FloatingWindowService.class))
                startService(new Intent(getApplicationContext(), FloatingWindowService.class));*/
            Log.i("MainActivity: ", "Created");

            //CAMERA PART
            textureView = (TextureView) findViewById(R.id.texture);
            assert textureView != null;
            textureView.setSurfaceTextureListener(textureListener);
            takePictureButton = (Button) findViewById(R.id.btn_enroll);
            testButton = (Button) findViewById(R.id.btn_test);
            trainButton = (Button) findViewById(R.id.btn_train);
            enterButton = (Button) findViewById(R.id.enter);
            loader = (ProgressBar) findViewById(R.id.loader);
            killButton = (Button) findViewById(R.id.kill);
            enrollName = (EditText) findViewById(R.id.enrollName);
            handip = (EditText) findViewById(R.id.ip);
            serverip = (EditText) findViewById(R.id.server);
            speaker = (NumberPicker) findViewById(R.id.speaker);
            text = (EditText) findViewById(R.id.text);
            enrollCb = (CheckBox) findViewById(R.id.enrollCb);
            bluetoothCb = (CheckBox) findViewById(R.id.bluetoothCb);
            localCb = (CheckBox) findViewById(R.id.localCb);
            videoCb = (CheckBox) findViewById(R.id.videoCb);
            googleCb = (CheckBox) findViewById(R.id.googleCb);
            enrollCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setPreferenceI("enroll", isChecked ? 1 : 0);
                }
            });
            bluetoothCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setPreferenceI("SetBluetooth", isChecked ? 1 : 0);
                }
            });

            localCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setPreferenceI("local", isChecked ? 1 : 0);
                    if(isChecked){
                        findViewById(R.id.spinner3).setVisibility(View.GONE);
                        findViewById(R.id.serverLayout).setVisibility(View.GONE);
                        findViewById(R.id.speakerLayout).setVisibility(View.GONE);
                        ValueAnimator anim = ValueAnimator.ofInt(resultView.getHeight(), 700); // Target height in pixels
                        anim.setDuration(300); // Animation duration
                        anim.addUpdateListener(animation -> {
                            ViewGroup.LayoutParams params = resultView.getLayoutParams();
                            params.height = (int) animation.getAnimatedValue();
                            resultView.setLayoutParams(params);
                        });
                        anim.start();
                    }
                    else{
                        findViewById(R.id.spinner3).setVisibility(View.VISIBLE);
                        findViewById(R.id.serverLayout).setVisibility(View.VISIBLE);
                        findViewById(R.id.speakerLayout).setVisibility(View.VISIBLE);
                        ValueAnimator anim = ValueAnimator.ofInt(resultView.getHeight(), 450); // Target height in pixels
                        anim.setDuration(300); // Animation duration
                        anim.addUpdateListener(animation -> {
                            ViewGroup.LayoutParams params = resultView.getLayoutParams();
                            params.height = (int) animation.getAnimatedValue();
                            resultView.setLayoutParams(params);
                        });
                        anim.start();
                    }
                }
            });

            videoCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setPreferenceI("video", isChecked ? 1 : 0);
                }
            });

            googleCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setPreferenceI("google", isChecked ? 1 : 0);
                    /*if(Tasks.globalWebSocket != null)
                        Tasks.globalWebSocket.send("google:"+ (isChecked ? 1 : 0)); */
                }
            });
            assert takePictureButton != null;
            takePictureButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takePicture(false);
                }
            });
            assert testButton != null;
            testButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takePicture(true);
                }
            });
            assert trainButton != null;
            trainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loader.setVisibility(View.VISIBLE);
                    new Handler().postDelayed(
                            new Runnable() {
                                public void run() {
                                    new Tasks.TrainTask().execute(Objects.requireNonNull(getExternalFilesDir(Environment.DIRECTORY_PICTURES)));
                                    //OpenCVFaceRecognizer.GetInstance().Train(Objects.requireNonNull(getExternalFilesDir(Environment.DIRECTORY_PICTURES)));
                                    loader.setVisibility(View.GONE);
                                }
                            },
                            (1000));
                }
            });

            enterButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Tasks.ActionTask.Task.doInBackground(text.getText().toString());
                        resultView.append("\nuser:\n"+text.getText().toString()+"\n");
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });
            handip.setText(getPreferenceS("handip"));
            handip.addTextChangedListener(new TextWatcher() {

                public void afterTextChanged(Editable s) {
                    setPreferenceS("handip", s.toString());
                }

                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            serverip.setText(getPreferenceS("serverip"));
            serverip.addTextChangedListener(new TextWatcher() {

                public void afterTextChanged(Editable s) {
                    setPreferenceS("serverip", s.toString());
                }

                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            speaker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            speaker.setWrapSelectorWheel(false);
            speaker.setMaxValue(12);
            speaker.setMinValue(0);
            speaker.setEnabled(true);
            speaker.setValue(getPreferenceI("speaker"));
            speaker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                @Override
                public void onValueChange(NumberPicker numberPicker, int i, int i1) {
                    speaker.setValue(numberPicker.getValue());
                    setPreferenceI("speaker", numberPicker.getValue());
                }
            });

            text.addTextChangedListener(new TextWatcher() {

                public void afterTextChanged(Editable s) {
                   // Tasks.ActionTask.Task.doInBackground(s.toString());
                }

                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            if(getPreferenceI("local") == 1)
                LLM.getInstance(getApplicationContext(), progressListener);

            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                    Log.e("Alert","Lets See if it Works !!!");
                }
            });

            //trainButton.performClick();

            //new Tasks.TrainTask().execute(Objects.requireNonNull(getExternalFilesDir(Environment.DIRECTORY_PICTURES)));
            //OpenCVFaceRecognizer.GetInstance().Train(Objects.requireNonNull(getExternalFilesDir(Environment.DIRECTORY_PICTURES)));
        }
        catch(Exception e){
            e.printStackTrace();
            Log.e("onCreate", e.getMessage());
        }
    }
    public OutputHandler.ProgressListener progressListener = ((partialResult, done) -> {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultView.append(partialResult.toString());
            }
        });
    });
    public PropertyChangeListener listener = evt -> {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Pair<String, Boolean> result = (Pair<String, Boolean>) evt.getNewValue();
                String partialText = result.first;
                resultView.append(partialText);
            }
        });


    };

    public PropertyChangeListener loadingListener = evt -> {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Boolean result = (Boolean) evt.getNewValue();
                if(result){
                    loader.setVisibility(View.VISIBLE);
                    findViewById(R.id.stop).setVisibility(View.VISIBLE);
                }
                else{
                    loader.setVisibility(View.GONE);
                    if(Tasks.AudioTracks.size() == 0)
                        findViewById(R.id.stop).setVisibility(View.GONE);
                }
            }
        });
    };
    ///CAMERA PART
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            if(getPreferenceI("enroll")!= 1) return;
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };
    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private File createImageFile(String name) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };
        File[] imageFiles = storageDir.listFiles(imgFilter);
        Integer counter = 0 ;
        Integer label = 0;
        ArrayList<Integer> labels = new ArrayList<>();
        for (File image : imageFiles) {
            if(image.getName().contains(name)){
                label = Integer.parseInt(image.getName().split("\\-")[0]);
                Log.i("LABEL:   ->>>>>>>>> ", String.valueOf(label));
                counter++;
            }
            else labels.add(Integer.parseInt(image.getName().split("\\-")[0]));
        }
        counter++;
        if(labels.size() == 0) label = 1;
        String finalLabel = label != 0
                ? String.valueOf(label)
                : String.valueOf(Collections.max(labels)+1);
        String imageFileName =  finalLabel+"-" + name+"-"+timeStamp.split("_")[1].substring(0, 4);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        //currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private File createRecognizedFile(String name) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File directory = new File(storageDir+File.separator+"recognized");
        directory.mkdir();
        for (File child : directory.listFiles())
            child.delete();
        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };
        String imageFileName = name+"-"+timeStamp.split("_")[1].substring(0, 4);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                directory      /* directory */
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    protected void takePicture(Boolean test) {
        if(test == null) test = false;
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = test ? createRecognizedFile(enrollName.getText().toString()) : createImageFile(enrollName.getText().toString());
            Boolean finalTest = test;
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                                try {
                                    ArrayList<Mat> faces = new Tasks.FaceDetectTask().execute(file).get();
                                    //OpenCVFaceRecognizer.GetInstance().DetectFaces(file);
                                    if(finalTest){
                                        String name = new Tasks.FaceRecognizerTask().execute(faces.get(0)).get();
                                        //String name = OpenCVFaceRecognizer.GetInstance().Recognize(file);
                                        Toast.makeText(getApplicationContext(), name, Toast.LENGTH_SHORT).show();
                                    }
                                }catch(Exception e){
                                    Log.e("OPENCVDETECT", e.getMessage());
                                }
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    protected void onStop(){
        super.onStop();
       /* // Delete local cache dir (ignoring any errors):
        FileUtils.deleteQuietly(getApplicationContext().getCacheDir());
        Cache.RemoveCache(getApplicationContext());
        Cache.ClearCache(getApplicationContext());
        Cache.DeleteCache(getApplicationContext());*/
    }


    @Override
    protected void onStart() {
        try{

            super.onStart();
            Log.i("MainActivity", "Started");
            int tempKeyword = getPreferenceI("KeywordFilePosition");
            int tempModel = getPreferenceI("ModelFilePosition");
            int tempLlmModel = getPreferenceI("LlmModelFilePosition");
            int tempSetEnroll = getPreferenceI("enroll");
            int tempSetBluetooth = getPreferenceI("SetBluetooth");
            int tempSetLocal = getPreferenceI("local");
            int tempSetVideo = getPreferenceI("video");
            int tempSetGoogle = getPreferenceI("google");
            String tempKeywordS = getPreferenceS("KeywordFileName");
            String tempModelS = getPreferenceS("ModelFileName");
            String tempLlmModelS = getPreferenceS("LlmModelFileName");
            handleSpinners();
            KeywordFileName = tempKeywordS;
            ModelFileName = tempModelS;
            LlmModelFileName = tempLlmModelS;
            KeywordSpinner.setSelection(tempKeyword);
            ModelSpinner.setSelection(tempModel);
            LlmModelSpinner.setSelection(tempLlmModel);
            enrollCb.setChecked(tempSetEnroll == 1);
            bluetoothCb.setChecked(tempSetBluetooth == 1);
            localCb.setChecked(tempSetLocal == 1);
            videoCb.setChecked(tempSetVideo == 1);
            googleCb.setChecked(tempSetGoogle == 1);
            setPreferenceI("KeywordFilePosition", tempKeyword);
            setPreferenceI("ModelFilePosition", tempModel);
            setPreferenceI("enroll", tempSetEnroll);
            setPreferenceI("SetBluetooth", tempSetBluetooth);
            setPreferenceI("local", tempSetLocal);
            setPreferenceI("video", tempSetVideo);
            setPreferenceI("google", tempSetGoogle);
            setPreferenceS("KeywordFileName", tempKeywordS);
            setPreferenceS("ModelFileName", tempModelS);
            setPreferenceS("LlmModelFileName", tempLlmModelS);
            // Recognizer initialization is a time-consuming and it involves IO,
            // so we execute it in async task
            if(CheckPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                new SetupTask(this).execute();

            new android.os.Handler().postDelayed(
                    () -> {
                        if(Tasks.ActionTask.propertyChangeSupport != null){
                            PropertyChangeListener[] listeners = Tasks.ActionTask.propertyChangeSupport.getPropertyChangeListeners("partialResult");
                            for (PropertyChangeListener l : listeners) {
                                Tasks.ActionTask.propertyChangeSupport.removePropertyChangeListener("partialResult", l);
                            }
                            Tasks.ActionTask.propertyChangeSupport.addPropertyChangeListener("partialResult", listener);
                            PropertyChangeListener[] loadingListeners = Tasks.ActionTask.propertyChangeSupport.getPropertyChangeListeners("loading");
                            for (PropertyChangeListener l : loadingListeners) {
                                Tasks.ActionTask.propertyChangeSupport.removePropertyChangeListener("loading", l);
                            }
                            Tasks.ActionTask.propertyChangeSupport.addPropertyChangeListener("loading", loadingListener);
                        }
                    },
                    2000);

            ///camera part
            if(tempSetEnroll != 1) return;
            enrollName.setVisibility(View.VISIBLE);
            takePictureButton.setVisibility(View.VISIBLE);
            testButton.setVisibility(View.VISIBLE);
            textureView.setVisibility(View.VISIBLE);
            startBackgroundThread();
            if (textureView.isAvailable()) {
                openCamera();
            } else {
                textureView.setSurfaceTextureListener(textureListener);
            }
        }
        catch(Exception e){
            Log.e("OnStart", e.getMessage());
        }
    }

    public class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<MainActivity> activityReference;

        SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                Log.d("KaldiDemo", "Sync files in the folder " + assetDir.toString());

                Vosk.SetLogLevel(0);

                activityReference.get().model = new Model(assetDir.toString() + "/"+ getPreferenceS("ModelFileName"));
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                activityReference.get().setErrorState(String.format(activityReference.get().getString(R.string.failed), result));
            } else {
                activityReference.get().setUiState(STATE_READY);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onResume() {
        super.onResume();

        if (Settings.canDrawOverlays(this)) {
          /*stopService(FloatingWindowService.class);
        startService(new Intent(getApplicationContext(), FloatingWindowService.class));*/
        }
        else {
            checkDrawOverlayPermission();
        }
        ///camera part
        if(setEnroll != 1) return;
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("MainActivity: ", "Paused");

        /*stopService(FloatingWindowService.class);
        startService(new Intent(getApplicationContext(), FloatingWindowService.class));*/
        //CAMERA PART
        if(setEnroll != 1) return;
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        /*// Delete local cache dir (ignoring any errors):
        FileUtils.deleteQuietly(getApplicationContext().getCacheDir());
        Cache.RemoveCache(getApplicationContext());
        Cache.ClearCache(getApplicationContext());
        Cache.DeleteCache(getApplicationContext());
*/
        super.onDestroy();
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
        Log.i("MainActivity: ", "Destroyed");
        if(setEnroll != 1) return;
        closeCamera();
        stopBackgroundThread();
        /*stopService(FloatingWindowService.class);
        startService(new Intent(getApplicationContext(), FloatingWindowService.class));*/

    }

    @Override
    public void onResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
        JSONObject reader = null;
        try {
            reader = new JSONObject(hypothesis);
            String text = reader.getString("text");
            Tasks.ActionTask.Task.doInBackground(text);
            //new Tasks.ActionTask(this).execute(text); // does not work
            //Tasks.ActionTask.Task.execute(text); // does not work
            recognizeMicrophone();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        recognizer.cancel();
        recognizer = null;
        setUiState(STATE_READY);
    }

    @SuppressLint("LongLogTag")
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i("RequestPermissionResults: ", String.valueOf(grantResults.length));
        Log.i("RequestPermissionRequestCode ", String.valueOf(requestCode));
        Log.i("RequestPermissions ", Arrays.toString(permissions));
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                //new SetupTask(this).execute();
                checkDrawOverlayPermission();
            } else {
                System.exit(0);
            }
        }
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_mic).setEnabled(false);
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_FILE:
                resultView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        //findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void PopulateSpinners(){
        KeywordSpinner = findViewById(R.id.keyword_spinner);
        ModelSpinner = findViewById(R.id.model_spinner);
        LlmModelSpinner = findViewById(R.id.llm_model_spinner);

        ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(
                this,
                R.array.keywords,
                R.layout.keyword_spinner_item);
        adapter1.setDropDownViewResource(R.layout.keyword_spinner_item);
        KeywordSpinner.setAdapter(adapter1);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(
                this,
                R.array.models,
                R.layout.keyword_spinner_item);
        adapter2.setDropDownViewResource(R.layout.keyword_spinner_item);
        ModelSpinner.setAdapter(adapter2);

        ArrayAdapter<CharSequence> adapter3 = ArrayAdapter.createFromResource(
                this,
                R.array.llm_models,
                R.layout.keyword_spinner_item);
        adapter3.setDropDownViewResource(R.layout.keyword_spinner_item);
        LlmModelSpinner.setAdapter(adapter3);

        KeywordSpinner.setSelection(getPreferenceI("KeywordFilePosition"));
        ModelSpinner.setSelection(getPreferenceI("ModelFilePosition"));
        LlmModelSpinner.setSelection(getPreferenceI("LlmModelFilePosition"));
    }

    private void handleSpinners() {
        KeywordSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
                    public void onItemSelected(AdapterView<?> parentView, View selectedItemView,
                                               int position, long id) {
                        Log.i("KeywordSpinner", "called");
                        KeywordFileName = KeywordSpinner.getSelectedItem().toString().toLowerCase().replaceAll("\\s+", "_")+"_android.ppn";
                        setPreferenceI("KeywordFilePosition", position);
                        setPreferenceS("KeywordFileName", KeywordFileName);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parentView) {
                        // Do nothing.
                    }
                });
        ModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView,
                                       int position, long id) {
                Log.i("ModelSpinner", "called");

                KeywordFileName = KeywordSpinner.getSelectedItem().toString().toLowerCase().replaceAll("\\s+", "_")+"_android.ppn";
                ModelFileName = "model-android-"+ModelSpinner.getSelectedItem().toString().toLowerCase();
                setPreferenceI("ModelFilePosition", position);
                setPreferenceS("ModelFileName", ModelFileName);

                /*if(Tasks.globalWebSocket != null)
                    Tasks.globalWebSocket.send("lang:"+ModelSpinner.getSelectedItem().toString().toLowerCase());
                */
                //stopService(AssistantService.class);
                //startAssistantService(KeywordFileName, ModelFileName);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Do nothing.
            }
        });

        LlmModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView,
                                       int position, long id) {
                Log.i("LlmModelSpinner", "called");
                LlmModelFileName =  LlmModelSpinner.getSelectedItem().toString();
                setPreferenceI("LlmModelFilePosition", position);
                setPreferenceS("LlmModelFileName", LlmModelFileName);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Do nothing.
            }
        });
    }

    public void getWakeWordAndStartService(boolean... rs){
        boolean restart = (rs.length >= 1) && rs[0];
        String kwd = KeywordSpinner.getSelectedItem().toString();
        String filename = kwd.toLowerCase().replaceAll("\\s+", "_")+"_android.ppn";
        if(restart){
            stopService(AssistantService.class);
            startAssistantService(filename, getPreferenceS("ModelFileName"));
            Tasks.restartMainActivity(getApplicationContext());
        }
        else if(!Tasks.isMyServiceRunning(getApplicationContext(),AssistantService.class))
            startAssistantService(filename, getPreferenceS("ModelFileName"));
    }

    public void recognizeMicrophone() {
        if (recognizer != null) {
            setUiState(STATE_DONE);
            recognizer.cancel();
            recognizer = null;
        } else {
            setUiState(STATE_MIC);
            try {
                recognizer = new SpeechRecognizer(model);
                recognizer.addListener(this);
                recognizer.startListening();
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    public void startAssistantService(String keywordFileName, String... modelFileName) {
        Intent serviceIntent = new Intent(this, AssistantService.class);
        serviceIntent.putExtra("keywordFileName", keywordFileName);
        if(modelFileName.length>=1)
            serviceIntent.putExtra("modelFileName", modelFileName[0]);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    public void stopService(Class classname) {
        Intent serviceIntent = new Intent(this, classname);
        stopService(serviceIntent);
    }

    private final static int REQUEST_CODE = 10101;

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkDrawOverlayPermission() {

        // Checks if app already has permission to draw overlays
        if (!Settings.canDrawOverlays(this)) {

            // If not, form up an Intent to launch the permission request
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));

            // Launch Intent, with the supplied request code
            startActivityForResult(intent, REQUEST_CODE);

            checkSetSettingsPermission();
        }
    }

    private void checkSetSettingsPermission() {
        // If not, form up an Intent to launch the permission request
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
        // Launch Intent, with the supplied request code
        startActivityForResult(intent, REQUEST_CODE);
    }

//UTILS

    /*new android.os.Handler().postDelayed(
               new Runnable() {
                   public void run() {
                       finish();
                   }
               },
               2000);*/
    private void setPreferenceS(String key, String value){
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private String getPreferenceS(String key){
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getString(key, "");
    }

    private void setPreferenceI(String key, int value){
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    private int getPreferenceI(String key){
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getInt(key, 0);
    }
    private void copyResourceFile(int resourceID, String filename) throws IOException {
        Resources resources = getResources();
        try (InputStream is = new BufferedInputStream(resources.openRawResource(resourceID), 256); OutputStream os = new BufferedOutputStream(openFileOutput(filename, Context.MODE_PRIVATE), 256)) {
            int r;
            while ((r = is.read()) != -1) {
                os.write(r);
            }
            os.flush();
        }catch (Exception e){
            Log.e("Error Copy Ressource File: ", e.getMessage());
        }
    }

    public int CheckPermission(String permission){
        try{
            int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), permission);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, new String[]{permission}, 1);
            return permissionCheck;
        }catch(Exception e){
            Log.e("Permissions", e.getMessage());
            return 0;
        }
    }

    public void CheckPermissions(ArrayList<String> permissions) {
        try {
            ArrayList<String> toAsk = new ArrayList<String>() {
            };
            for (String permission : permissions) {
                int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), permission);
                if (permissionCheck != PackageManager.PERMISSION_GRANTED)
                    toAsk.add(permission);
            }
            if (toAsk.size() > 0)
                ActivityCompat.requestPermissions(this, toAsk.toArray(new String[0]), 1);
        } catch (Exception e) {
            Log.e("Permissions", e.getMessage());
        }
    }

}
