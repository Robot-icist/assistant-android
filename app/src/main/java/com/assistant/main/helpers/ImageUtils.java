package com.assistant.main.helpers;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageUtils {

    // Resize and compress the image, and return the resulting byte array
    public static byte[] resizeAndCompressImage(byte[] imageBytes, int quality) throws IOException {
        // Convert byte[] to Bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        // Resize the image to a reasonable size if it's too large
        Bitmap resizedBitmap = resizeImage(bitmap);

        // Compress the image to a byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        // Return the byte array
        return baos.toByteArray();
    }

    // Resize the image to a reasonable size if it's too large
    private static Bitmap resizeImage(Bitmap originalBitmap) {
        int maxWidth = 1000; // Max width to prevent large images
        int maxHeight = 1000; // Max height to prevent large images

        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();

        // If the image is larger than the max width or height, scale it down
        if (width > maxWidth || height > maxHeight) {
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;

            // Maintain aspect ratio
            if (ratioMax > ratioBitmap) {
                finalWidth = (int) (maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) (maxWidth / ratioBitmap);
            }

            return Bitmap.createScaledBitmap(originalBitmap, finalWidth, finalHeight, true);
        }

        // If the image is small enough, no resizing is needed
        return originalBitmap;
    }
}
