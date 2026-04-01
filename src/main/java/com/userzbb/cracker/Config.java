package com.userzbb.cracker;

/**
 * Configuration constants for the HTTP cracker.
 * Matches src/config.py from UJN_lib_scaper.
 */
public class Config {
    public static final String BASE_URL = "https://seat.ujn.edu.cn";
    public static final String LOGIN_API = BASE_URL + "/rest/auth";
    public static final String CAPTCHA_API = BASE_URL + "/auth/createCaptcha";

    // HMAC secret - matches Python config
    public static final String HMAC_SECRET = "ujnLIB2022tsg";
    // AES key - matches Python config
    public static final String AES_KEY = "server_date_time";
    // AES IV - matches Python config
    public static final String AES_IV = "client_date_time";

    // Request timeout in milliseconds
    public static final int REQUEST_TIMEOUT_MS = 8000;

    // Database path - relative path for portability across different computers
    public static final String DB_PATH = "crack_progress.db";
}
