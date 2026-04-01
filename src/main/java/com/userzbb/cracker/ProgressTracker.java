package com.userzbb.cracker;

/**
 * Tracks progress state with high-water mark for password cracking.
 * Only updates if the new password is greater than the current high-water mark.
 *
 * 实验要求：
 * 1. 包含两个成员变量（username, currentPassword等）
 * 2. 包含成员方法（updateIfHigher, incrementAttempts等）
 * 3. 包含有参构造方法和无参构造方法
 * 4. 使用this关键字区分成员变量和参数
 * 5. 使用static关键字定义常量
 */
public class ProgressTracker {

    // static关键字：所有实例共享的常量
    private static final String DEFAULT_USERNAME = "unknown";

    // 成员变量（使用this关键字区分）
    private String username;
    private String currentPassword;
    private int attemptCount;
    private String highWaterMark;
    private volatile boolean running;

    /**
     * 无参构造方法
     * 使用this关键字调用有参构造方法
     */
    public ProgressTracker() {
        this(DEFAULT_USERNAME);
        System.out.println("   [无参构造被调用] 使用默认用户名: " + this.username);
    }

    /**
     * 有参构造方法
     * 使用this关键字初始化成员变量
     */
    public ProgressTracker(String username) {
        this.username = username;
        this.currentPassword = "";
        this.attemptCount = 0;
        this.highWaterMark = "";
        this.running = true;
    }

    /**
     * 显示当前状态
     * 演示成员变量的访问
     */
    public void display() {
        System.out.println("   username = " + this.username);
        System.out.println("   currentPassword = " + this.currentPassword);
        System.out.println("   attemptCount = " + this.attemptCount);
        System.out.println("   highWaterMark = " + this.highWaterMark);
        System.out.println("   running = " + this.running);
    }

    /**
     * Updates progress if the new password is greater than the current high-water mark.
     * Password comparison is done lexicographically.
     */
    public synchronized boolean updateIfHigher(String password) {
        if (password.compareTo(highWaterMark) > 0) {
            highWaterMark = password;
            currentPassword = password;
            return true;
        }
        return false;
    }

    /**
     * Increments the attempt counter.
     */
    public synchronized void incrementAttempts() {
        attemptCount++;
    }

    /**
     * Gets the current attempt count.
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * Gets the current password being processed.
     */
    public String getCurrentPassword() {
        return currentPassword;
    }

    /**
     * Gets the high-water mark password.
     */
    public String getHighWaterMark() {
        return highWaterMark;
    }

    /**
     * Checks if cracking is still running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Stops the cracking process.
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Sets the initial resume point from database.
     */
    public void setResumePoint(String resumePassword) {
        if (resumePassword != null && !resumePassword.isEmpty()) {
            this.highWaterMark = resumePassword;
            this.currentPassword = resumePassword;
        }
    }

    /**
     * Gets the username.
     */
    public String getUsername() {
        return username;
    }
}
