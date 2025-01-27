package com.assistant.main.helpers;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

public class FileUtils {

    public static byte[] getFileBytes(String filePath) throws IOException {
        File file = new File(filePath);

        // Check if file exists
        if (!file.exists()) {
            throw new IOException("File does not exist at path: " + filePath);
        }

        // Get absolute path
        String absolutePath = file.getAbsolutePath();
        System.out.println("Absolute Path: " + absolutePath);

        // Read file into byte array
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
}
