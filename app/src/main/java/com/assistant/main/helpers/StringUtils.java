package com.assistant.main.helpers;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class StringUtils {

    // Pre-compile the regex pattern for efficiency.
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Removes diacritical marks (accents) from a string.
     * For example, "crème brûlée" becomes "creme brulee".
     *
     * @param input The string to de-accent.
     * @return The string without diacritics, or null if the input was null.
     */
    public static String removeDiacritics(String input) {
        if (input == null) {
            return null;
        }

        // 1. Normalize the string to NFD (Canonical Decomposition)
        // This separates base characters from their combining marks.
        // e.g., "é" becomes "e" + "´" (combining acute accent)
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

        // 2. Use a regex to remove the combining marks
        // The \p{M} or \p{InCombiningDiacriticalMarks} block matches the accent characters.
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }

//    // --- Example Usage ---
//    public static void main(String[] args) {
//        String originalText1 = "Crème brûlée à l'orange";
//        String originalText2 = "Příliš žluťoučký kůň úpěl ďábelské ódy.";
//        String originalText3 = "El niño jugaba con su piñata.";
//        String originalText4 = "İstanbul'a hoş geldiniz.";
//
//        System.out.println("Original:  " + originalText1);
//        System.out.println("Cleaned:   " + removeDiacritics(originalText1));
//        System.out.println("---");
//
//        System.out.println("Original:  " + originalText2);
//        System.out.println("Cleaned:   " + removeDiacritics(originalText2));
//        System.out.println("---");
//
//        System.out.println("Original:  " + originalText3);
//        System.out.println("Cleaned:   " + removeDiacritics(originalText3));
//        System.out.println("---");
//
//        System.out.println("Original:  " + originalText4);
//        System.out.println("Cleaned:   " + removeDiacritics(originalText4));
//    }
}
