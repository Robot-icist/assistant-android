package com.assistant.main.camera;

import android.os.AsyncTask;

import com.assistant.main.AssistantService;

import java.lang.ref.WeakReference;

public class CameraTask extends AsyncTask<String, Void, Void> {
    WeakReference<AssistantService> serviceReference;

    public CameraTask(WeakReference<AssistantService> serviceReference){
        this.serviceReference = serviceReference;
    }

    @Override
    protected Void doInBackground(String... params) {
        Camera cam = new Camera(serviceReference.get());
        cam.startBackgroundThread();
        cam.openCamera();
        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        cam.takePicture(serviceReference);
                    }
                },
                2000);
        return null;
    }
}
