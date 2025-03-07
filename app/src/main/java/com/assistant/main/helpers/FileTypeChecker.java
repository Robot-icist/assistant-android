package com.assistant.main.helpers;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FileTypeChecker {
    /**
     * Checks if a byte array is of type WAV or MP4.
     *
     * @param fileData The byte array containing the file data.
     * @return "WAV" if it's a WAV file, "MP4" if it's an MP4 file, or "Unknown" otherwise.
     */
    public static String getFileType(byte[] fileData) {
        if (fileData == null || fileData.length < 12) {
            return "Unknown"; // Too short to determine file type
        }

        // Check for WAV (RIFF header)
        if (isWav(fileData)) {
            return "WAV";
        }

        // Check for MP4 (ftyp header)
        if (isMp4(fileData)) {
            return "MP4";
        }

        return "Unknown";
    }

    public static boolean isWav(byte[] fileData) {
        // WAV files start with "RIFF" followed by "WAVE"
        String chunkId = new String(fileData, 0, 4); // "RIFF"
        String format = new String(fileData, 8, 4); // "WAVE"
        return chunkId.equals("RIFF") && format.equals("WAVE");
    }

    public static boolean isMp4(byte[] fileData) {
        // MP4 files start with "ftyp" in bytes 4-7
        // Check if the first 4 bytes represent a valid box size (big-endian integer)
        ByteBuffer buffer = ByteBuffer.wrap(fileData, 0, 4).order(ByteOrder.BIG_ENDIAN);
        int boxSize = buffer.getInt();
        if (boxSize < 8 || boxSize > fileData.length) {
            return false; // Invalid MP4 box size
        }

        // Check for "ftyp" in bytes 4-7
        String boxType = new String(fileData, 4, 4); // "ftyp"
        return boxType.equals("ftyp");
    }

    /**
     * Checks if a byte array represents an image (PNG, JPEG, or GIF).
     *
     * @param fileData The byte array containing the file data.
     * @return true if the data represents an image, false otherwise.
     */
    public static boolean isImage(byte[] fileData) {
        if (fileData == null || fileData.length < 4) {
            return false; // Too short to be a valid image
        }

        return isPng(fileData) || isJpeg(fileData) || isGif(fileData);
    }

    private static boolean isPng(byte[] fileData) {
        // PNG files start with the following byte sequence: 89 50 4E 47 0D 0A 1A 0A
        if (fileData.length < 8) return false;
        return fileData[0] == (byte) 0x89 &&
                fileData[1] == (byte) 0x50 &&
                fileData[2] == (byte) 0x4E &&
                fileData[3] == (byte) 0x47 &&
                fileData[4] == (byte) 0x0D &&
                fileData[5] == (byte) 0x0A &&
                fileData[6] == (byte) 0x1A &&
                fileData[7] == (byte) 0x0A;
    }

    private static boolean isJpeg(byte[] fileData) {
        // JPEG files start with FF D8 FF
        if (fileData.length < 3) return false;
        return fileData[0] == (byte) 0xFF &&
                fileData[1] == (byte) 0xD8 &&
                fileData[2] == (byte) 0xFF;
    }

    private static boolean isGif(byte[] fileData) {
        // GIF files start with GIF87a or GIF89a
        if (fileData.length < 6) return false;
        String header = new String(fileData, 0, 6);
        return header.startsWith("GIF87a") || header.startsWith("GIF89a");
    }
}
