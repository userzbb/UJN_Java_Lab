package com.userzbb.cracker;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.gcszhn.d4ocr.OCREngine;

/**
 * Captcha solver using ddddocr (deep learning based OCR).
 * Pure Java implementation - no native dependencies!
 *
 * Matches src/utils/captcha.py from UJN_lib_scaper.
 */
public class CaptchaSolver {

    // Singleton OCR engine (thread-safe and reusable)
    private static final OCREngine OCR_ENGINE = OCREngine.instance();

    // Shared HTTP client with timeout
    private static final CloseableHttpClient HTTP_CLIENT;

    private static final int REQUEST_TIMEOUT_SECONDS = 8;

    static {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .setResponseTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .build();
        HTTP_CLIENT = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    /**
     * Result class to hold captchaId and captcha code.
     */
    public static class CaptchaResult {
        public final String captchaId;
        public final String code;

        public CaptchaResult(String captchaId, String code) {
            this.captchaId = captchaId;
            this.code = code;
        }
    }

    /**
     * Fetches captcha from API and solves it.
     * Returns CaptchaResult with captchaId and solved code.
     * Matches Python solve_captcha() function.
     */
    public static CaptchaResult fetchAndSolve() throws Exception {
        HttpGet request = new HttpGet(Config.CAPTCHA_API);
        request.setHeader("User-Agent", "Mozilla/5.0");

        try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
            int statusCode = response.getCode();
            if (statusCode != 200) {
                System.err.println("[Captcha] HTTP " + statusCode);
                return null;
            }

            String responseBody = EntityUtils.toString(response.getEntity());
            return parseAndSolve(responseBody);
        }
    }

    /**
     * Parses JSON response and solves captcha image.
     */
    private static CaptchaResult parseAndSolve(String jsonResponse) throws Exception {
        // Parse JSON manually (simple approach)
        String captchaId = extractJsonValue(jsonResponse, "captchaId");
        String imgB64 = extractJsonValue(jsonResponse, "captchaImage");

        if (captchaId == null || imgB64 == null || imgB64.isEmpty()) {
            System.err.println("[Captcha] Missing captchaId or image");
            return null;
        }

        // Strip prefix if present (e.g., "data:image/png;base64,...")
        if (imgB64.contains(",")) {
            imgB64 = imgB64.split(",")[1];
        }

        // Decode base64 and solve
        byte[] imgBytes = Base64.getDecoder().decode(imgB64);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imgBytes));

        if (image == null) {
            System.err.println("[Captcha] Failed to decode image");
            return null;
        }

        // Use ddddocr to recognize
        String code = OCR_ENGINE.recognize(image);

        return new CaptchaResult(captchaId, code);
    }

    /**
     * Extracts a string value from simple JSON.
     */
    private static String extractJsonValue(String json, String key) {
        // Simple regex-based JSON parsing
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Saves captcha image to disk for debugging.
     */
    public static void saveCaptchaImage(String base64Image, String filename) throws Exception {
        String imgB64 = base64Image;
        if (imgB64.contains(",")) {
            imgB64 = imgB64.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(imgB64);
        Files.write(new File(filename).toPath(), imageBytes);
    }
}
