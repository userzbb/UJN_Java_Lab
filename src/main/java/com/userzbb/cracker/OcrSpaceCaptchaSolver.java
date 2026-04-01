package com.userzbb.cracker;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captcha solver using OCR.space API.
 * Pure Java solution - no native dependencies required!
 *
 * OCR.space provides free OCR API with 500 requests/hour.
 * Sign up at: https://ocr.space/ocrapi
 */
public class OcrSpaceCaptchaSolver {

    // OCR.space free API key (use your own from https://ocr.space/ocrapi)
    // This is a demo key with limited usage
    private static final String OCR_API_KEY = "helloworld";
    private static final String OCR_API_URL = "https://api.ocr.space/parse/image";

    /**
     * Fetches captcha image from the target API.
     */
    public static String fetchCaptcha() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(Config.CAPTCHA_API);
            String[] headers = CryptoUtils.generateHeaders("GET");

            request.setHeader("X-Timestamp", headers[0]);
            request.setHeader("X-Nonce", headers[1]);
            request.setHeader("X-Signature", headers[2]);

            try (CloseableHttpResponse response = client.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                return parseCaptchaFromResponse(responseBody);
            }
        }
    }

    /**
     * Solves captcha using OCR.space cloud API.
     */
    public static String solveCaptcha(String base64Image) throws Exception {
        // Decode base64 to image
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        // Call OCR.space API
        String ocrResult = callOcrSpaceApi(imageBytes);

        // Parse result - OCR.space returns JSON with parsed text
        String solved = parseOcrResult(ocrResult);

        System.out.println("[Captcha] OCR solved: " + solved);
        return solved;
    }

    /**
     * Full captcha fetch and solve operation.
     */
    public static String solve() throws Exception {
        String base64Image = fetchCaptcha();
        return solveCaptcha(base64Image);
    }

    /**
     * Calls OCR.space API to perform OCR on the image.
     */
    private static String callOcrSpaceApi(byte[] imageBytes) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(OCR_API_URL);

            // Build multipart form data
            // OCR.space expects base64 encoded image in form field
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String body = "apikey=" + OCR_API_KEY + "&base64Image=data:image/png;base64," + base64Image + "&OCREngine=2";

            request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            request.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));

            try (CloseableHttpResponse response = client.execute(request)) {
                return EntityUtils.toString(response.getEntity());
            }
        }
    }

    /**
     * Parses OCR result from OCR.space JSON response.
     * Expected format: {"ParsedResults":[{"ParsedText":"1234"}],"OCRExitCode":100}
     */
    private static String parseOcrResult(String jsonResponse) {
        // Simple JSON parsing
        Pattern pattern = Pattern.compile("\"ParsedText\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(jsonResponse);

        if (matcher.find()) {
            String text = matcher.group(1).trim();
            // Remove whitespace and non-alphanumeric
            text = text.replaceAll("[^a-zA-Z0-9]", "");
            return text;
        }

        // Check for error codes
        if (jsonResponse.contains("\"OCRExitCode\":100")) {
            throw new IllegalStateException("OCR failed: " + jsonResponse);
        }
        if (jsonResponse.contains("\"IsErroredOnProcessing\":true")) {
            throw new IllegalStateException("OCR API error: " + jsonResponse);
        }

        throw new IllegalStateException("Failed to parse OCR result: " + jsonResponse);
    }

    /**
     * Saves captcha image to disk for debugging.
     */
    public static void saveCaptchaImage(String base64Image, String filename) throws Exception {
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(imageBytes);
        Files.write(new File(filename).toPath(), imageBytes);
    }

    private static String parseCaptchaFromResponse(String response) {
        int captchaIndex = response.indexOf("\"captcha\":\"");
        if (captchaIndex == -1) {
            captchaIndex = response.indexOf("\"captcha\": \"");
            if (captchaIndex == -1) {
                throw new IllegalStateException("Captcha field not found in response: " + response);
            }
            captchaIndex += "\"captcha\": \"".length();
        } else {
            captchaIndex += "\"captcha\":\"".length();
        }

        int endIndex = response.indexOf("\"", captchaIndex);
        if (endIndex == -1) {
            throw new IllegalStateException("Failed to parse captcha from response: " + response);
        }

        return response.substring(captchaIndex, endIndex);
    }
}
