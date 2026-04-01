package com.userzbb.cracker;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Pure Java OCR solver for simple numeric captchas.
 * No external dependencies required - 100% Java implementation.
 *
 * Uses pixel analysis and pattern matching for digit recognition.
 * Works best with simple, clean captchas (white background, black digits).
 */
public class PureJavaCaptchaSolver {

    // Digit patterns for recognition (5x3 pixel templates for digits 0-9)
    // Each digit is represented as 5 rows of 3 bits
    private static final int[][][] DIGIT_PATTERNS = {
        // 0 - hollow circle
        {{0,1,0},{1,0,1},{1,0,1},{1,0,1},{0,1,0}},
        // 1 - single line
        {{0,1,0},{1,1,0},{0,1,0},{0,1,0},{1,1,1}},
        // 2 - curved top
        {{0,1,0},{1,0,1},{0,0,1},{0,1,0},{1,1,1}},
        // 3 - curved top and bottom
        {{1,1,0},{0,0,1},{0,1,0},{0,0,1},{1,1,0}},
        // 4 - vertical lines
        {{1,0,1},{1,0,1},{0,1,1},{0,0,1},{0,0,1}},
        // 5 - curved bottom
        {{1,1,1},{1,0,0},{0,1,1},{0,0,1},{1,1,0}},
        // 6 - curved left
        {{0,1,0},{1,0,0},{1,1,0},{1,0,1},{0,1,0}},
        // 7 - diagonal
        {{1,1,1},{0,0,1},{0,1,0},{0,1,0},{0,1,0}},
        // 8 - full circle
        {{0,1,0},{1,0,1},{0,1,0},{1,0,1},{0,1,0}},
        // 9 - curved top
        {{0,1,0},{1,0,1},{0,1,1},{0,0,1},{1,1,0}}
    };

    /**
     * Solves captcha from base64-encoded image using pure Java OCR.
     */
    public static String solveCaptcha(String base64Image) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bais);

        if (image == null) {
            throw new IllegalStateException("Failed to decode captcha image");
        }

        return solve(image);
    }

    /**
     * Performs OCR on a BufferedImage.
     */
    public static String solve(BufferedImage image) throws Exception {
        // Preprocess: convert to grayscale and binarize
        BufferedImage processed = preprocess(image);

        // Find digit regions
        int[][] pixels = extractPixelMatrix(processed);

        // Split into 4 digit regions (assuming 4-character captcha)
        int width = pixels[0].length;
        int digitWidth = width / 4;

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < 4; i++) {
            int startX = i * digitWidth;
            int endX = startX + digitWidth;

            // Extract digit region (5x5 center area)
            int[][] digitRegion = new int[5][5];
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    int srcX = startX + (digitWidth * x / 5);
                    int srcY = (pixels.length * y / 5);
                    if (srcX < width && srcY < pixels.length) {
                        digitRegion[y][x] = pixels[srcY][srcX];
                    }
                }
            }

            int digit = recognizeDigit(digitRegion);
            result.append(digit);
        }

        String solved = result.toString();
        System.out.println("[Captcha] Pure Java OCR solved: " + solved);
        return solved;
    }

    /**
     * Preprocess image: grayscale + binarization.
     */
    private static BufferedImage preprocess(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage grayscale = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Convert to grayscale
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(original.getRGB(x, y));
                int gray = (int) (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114);
                grayscale.setRGB(x, y, new Color(gray, gray, gray).getRGB());
            }
        }

        // Binarize (threshold)
        int threshold = 128;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = new Color(grayscale.getRGB(x, y)).getRed();
                int binary = gray < threshold ? 0 : 1;
                grayscale.setRGB(x, y, binary == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        return grayscale;
    }

    /**
     * Extracts 2D pixel matrix from image.
     */
    private static int[][] extractPixelMatrix(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] pixels = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y));
                pixels[y][x] = c.getRed() < 128 ? 0 : 1;
            }
        }

        return pixels;
    }

    /**
     * Recognizes a single digit using template matching.
     */
    private static int recognizeDigit(int[][] region) {
        int bestMatch = 0;
        double bestScore = Double.MAX_VALUE;

        for (int d = 0; d <= 9; d++) {
            double score = comparePatterns(region, DIGIT_PATTERNS[d]);
            if (score < bestScore) {
                bestScore = score;
                bestMatch = d;
            }
        }

        return bestMatch;
    }

    /**
     * Compares digit region with template pattern.
     */
    private static double comparePatterns(int[][] region, int[][] pattern) {
        double distance = 0;
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 3; x++) {
                int rx = (x * 5) / 3; // Scale x to match template
                int ry = y;
                if (rx < 5) {
                    distance += Math.abs(region[ry][rx] - pattern[y][x]);
                }
            }
        }
        return distance;
    }

    /**
     * Saves captcha image for debugging.
     */
    public static void saveCaptchaImage(String base64Image, String filename) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        Files.write(new File(filename).toPath(), imageBytes);
    }
}
