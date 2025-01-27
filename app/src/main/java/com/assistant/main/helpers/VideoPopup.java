package com.assistant.main.helpers;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.os.Handler;
import android.os.Looper;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class VideoPopup {

    private final Context context;
    private WindowManager windowManager;
    private View overlayView;
    private boolean isVideoPlaying = false;
    private final Queue<byte[]> videoQueue = new LinkedList<>();

    public VideoPopup(Context context) {
        this.context = context.getApplicationContext();
    }

    public void showVideoPopup(byte[] videoData) {
        videoQueue.offer(videoData);
        playNextVideo();
    }

    private void playNextVideo() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (isVideoPlaying) return;
            if (videoQueue.isEmpty()) return;

            isVideoPlaying = true;
            byte[] videoData = videoQueue.poll();

            File tempVideoFile = null;
            try {
                String uniqueFileName = "temp_video_" + UUID.randomUUID().toString() + ".mp4";
                tempVideoFile = new File(context.getCacheDir(), uniqueFileName);
                try (FileOutputStream fos = new FileOutputStream(tempVideoFile)) {
                    fos.write(videoData);
                }
            } catch (IOException e) {
                e.printStackTrace();
                isVideoPlaying = false;
                playNextVideo();
                return;
            }

            VideoView videoView = new VideoView(context);
            videoView.setVideoPath(tempVideoFile.getAbsolutePath());

            // Center the video view and scale it to the maximum size
            FrameLayout videoContainer = new FrameLayout(context);
            videoContainer.setBackgroundColor(Color.BLACK);

            FrameLayout.LayoutParams videoLayoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            videoLayoutParams.gravity = Gravity.CENTER;
            videoView.setLayoutParams(videoLayoutParams);

            videoView.setOnPreparedListener(mediaPlayer -> {
                // Get screen dimensions
                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                int screenWidth = windowManager.getDefaultDisplay().getWidth();
                int screenHeight = windowManager.getDefaultDisplay().getHeight();

                // Get video dimensions
                int videoWidth = mediaPlayer.getVideoWidth();
                int videoHeight = mediaPlayer.getVideoHeight();

                // Calculate aspect ratio and scale
                float videoAspect = (float) videoWidth / videoHeight;
                float screenAspect = (float) screenWidth / screenHeight;

                if (videoAspect > screenAspect) {
                    // Video is wider than the screen, fit to screen width
                    int scaledHeight = (int) (screenWidth / videoAspect);
                    videoLayoutParams.width = screenWidth;
                    videoLayoutParams.height = scaledHeight;
                } else {
                    // Video is taller than the screen, fit to screen height
                    int scaledWidth = (int) (screenHeight / videoAspect);
                    videoLayoutParams.width = scaledWidth;
                    videoLayoutParams.height = screenHeight;
                }

                videoView.setLayoutParams(videoLayoutParams);
                mediaPlayer.setLooping(false);
                videoView.start();
            });

            videoView.setOnCompletionListener(i -> dismissPopup());

            videoContainer.addView(videoView);

            File finalTempVideoFile = tempVideoFile;
            videoContainer.setOnClickListener(v -> {
                dismissPopup();
                if (finalTempVideoFile.exists()) {
                    finalTempVideoFile.delete();
                }
            });

            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            overlayView = videoContainer;
            windowManager.addView(overlayView, params);
        });
    }

    public void dismissPopup() {
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        isVideoPlaying = false;
        playNextVideo();
    }
}
