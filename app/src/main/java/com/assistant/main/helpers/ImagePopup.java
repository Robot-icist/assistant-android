package com.assistant.main.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;

public class ImagePopup {

    private final Context context;
    private WindowManager windowManager;
    private View overlayView;

    public ImagePopup(Context context) {
        this.context = context.getApplicationContext();
    }

    public void showImagePopup(byte[] imageData) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            // Convert the byte array to a Bitmap
            Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(imageData));

            if (bitmap == null) {
                // If decoding fails, exit early (optional: handle error)
                return;
            }

            // Create the ImageView to display the image from the byte array
            ImageView imageView = new ImageView(context);
            imageView.setImageBitmap(bitmap);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            // Create a FrameLayout to hold the ImageView
            FrameLayout imageContainer = new FrameLayout(context);
            imageContainer.setBackgroundColor(Color.BLACK); // Optional: set the background color of the container

            // Set up layout parameters for the image
            FrameLayout.LayoutParams imageLayoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            imageLayoutParams.gravity = Gravity.CENTER;
            imageView.setLayoutParams(imageLayoutParams);

            // Set the onClick listener to dismiss the popup when clicked
            imageContainer.setOnClickListener(v -> dismissPopup());

            // Add the ImageView to the FrameLayout
            imageContainer.addView(imageView);

            // Get the WindowManager to show the view on top
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

            // Add the FrameLayout to the WindowManager
            overlayView = imageContainer;
            windowManager.addView(overlayView, params);
        });
    }

    public void dismissPopup() {
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }
}

