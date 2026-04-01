package com.userzbb.cracker;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQLite database manager for tracking crack progress and found passwords.
 * Mirrors src/core/database.py from UJN_lib_scaper.
 */
public class DatabaseManager {
    private final String dbPath;
    private final Connection connection;
    private final ConcurrentLinkedQueue<ProgressEntry> progressQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingProgressCount = new AtomicInteger(0);

    private static final int BATCH_SIZE = 50;
    private static final long BATCH_INTERVAL_MS = 2000;

    public DatabaseManager(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        initTables();
    }

    private void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Table for found passwords
            stmt.execute("CREATE TABLE IF NOT EXISTS found_passwords (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL," +
                "password TEXT NOT NULL," +
                "found_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE(username, password))");

            // Table for crack progress tracking
            stmt.execute("CREATE TABLE IF NOT EXISTS crack_progress (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL UNIQUE," +
                "current_password TEXT," +
                "attempt_count INTEGER DEFAULT 0," +
                "status TEXT DEFAULT 'running'," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Table for detailed progress
            stmt.execute("CREATE TABLE IF NOT EXISTS crack_progress_detail (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL," +
                "password TEXT NOT NULL," +
                "result TEXT NOT NULL," +
                "checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Index for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_progress_username ON crack_progress(username)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_detail_username ON crack_progress_detail(username)");
        }
    }

    /**
     * Records a found password.
     */
    public void recordFoundPassword(String username, String password) throws SQLException {
        String sql = "INSERT OR IGNORE INTO found_passwords (username, password) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
        }
    }

    /**
     * Checks if a password has already been found for the user.
     */
    public boolean isPasswordFound(String username, String password) throws SQLException {
        String sql = "SELECT COUNT(*) FROM found_passwords WHERE username = ? AND password = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Gets all found passwords for a user.
     */
    public List<String> getFoundPasswords(String username) throws SQLException {
        List<String> passwords = new ArrayList<>();
        String sql = "SELECT password FROM found_passwords WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    passwords.add(rs.getString("password"));
                }
            }
        }
        return passwords;
    }

    /**
     * Initializes or resumes progress for a username.
     * Returns the last attempted password, or null if starting fresh.
     */
    public String initializeProgress(String username) throws SQLException {
        String sql = "SELECT current_password FROM crack_progress WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("current_password");
                }
            }
        }

        // Insert new progress record
        String insertSql = "INSERT INTO crack_progress (username, current_password, attempt_count, status) VALUES (?, '', 0, 'running')";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        return null;
    }

    /**
     * Updates the current progress position.
     */
    public void updateProgress(String username, String currentPassword, int attemptCount) throws SQLException {
        String sql = "UPDATE crack_progress " +
            "SET current_password = ?, attempt_count = ?, updated_at = CURRENT_TIMESTAMP " +
            "WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, currentPassword);
            pstmt.setString(2, String.valueOf(attemptCount));
            pstmt.setString(3, username);
            pstmt.executeUpdate();
        }
    }

    /**
     * Marks cracking as complete for a user.
     */
    public void markComplete(String username) throws SQLException {
        String sql = "UPDATE crack_progress SET status = 'completed' WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
    }

    /**
     * Adds a progress entry to the queue for batch processing.
     */
    public void queueProgress(String username, String password, String result) {
        progressQueue.offer(new ProgressEntry(username, password, result));
        if (pendingProgressCount.incrementAndGet() >= BATCH_SIZE) {
            flushProgress();
        }
    }

    /**
     * Flushes queued progress entries to database.
     */
    public void flushProgress() {
        List<ProgressEntry> entries = new ArrayList<>();
        ProgressEntry entry;
        while ((entry = progressQueue.poll()) != null) {
            entries.add(entry);
            pendingProgressCount.decrementAndGet();
        }

        if (entries.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO crack_progress_detail (username, password, result) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (ProgressEntry e : entries) {
                pstmt.setString(1, e.username);
                pstmt.setString(2, e.password);
                pstmt.setString(3, e.result);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            connection.commit();
        } catch (SQLException ex) {
            System.err.println("Failed to flush progress: " + ex.getMessage());
            try {
                connection.rollback();
            } catch (SQLException ignored) {}
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Schedules periodic flush of progress entries.
     */
    public void startPeriodicFlush() {
        Thread flusherThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(BATCH_INTERVAL_MS);
                    flushProgress();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        flusherThread.setDaemon(true);
        flusherThread.start();
    }

    /**
     * Closes the database connection.
     */
    public void close() throws SQLException {
        flushProgress();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * Gets the crack status for a user.
     */
    public String getStatus(String username) throws SQLException {
        String sql = "SELECT status FROM crack_progress WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        }
        return "not_started";
    }

    private static class ProgressEntry {
        final String username;
        final String password;
        final String result;

        ProgressEntry(String username, String password, String result) {
            this.username = username;
            this.password = password;
            this.result = result;
        }
    }
}
