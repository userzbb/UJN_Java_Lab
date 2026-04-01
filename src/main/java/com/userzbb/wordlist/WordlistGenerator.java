package com.userzbb.wordlist;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

    /**
     * 字典生成器：
     * 支持两种模式：
     * 1) 通用字符集穷举（兼容 -min/-max/-cs）
     * 2) DDSSSC 专用生成（出生日期 + 顺序码 + 校验位），行为与仓库 src/core/generator.py 对齐
     *
     * 主要参数（与原仓库保持一致）：
     * --gender / -g <M|F|ALL>    性别（M 男 奇数顺序码，F 女 偶数顺序码，ALL 双性别）
     * --day / -d <01|01-05|01,03> 指定日期（支持单日、范围或逗号列表），默认 01..31
     * --max-seq / -s <N>         顺序码上限（与 Python range(max_seq) 对齐，exclusive），默认 500
     * username (位置参数)       目标学号，用于生成默认输出文件 passwords_{username}.txt
     *
     * 输出格式：每行以 CSV 格式写入 day_key,password，例如：M_08,080010
     */
public class WordlistGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String outPath = null;
        int min = 1;
        int max = 4;
        String cs = "lower";
        boolean overwrite = false;
        // DDSSSC pattern options (default mode)
        boolean ddsssc = true;
        String daySpec = null; // null means default 01..31
        char gender = 'A'; // M, F, A (A == ALL)
        int maxSeq = 500; // match repo default
        int startSeq = 0;
        String username = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-g".equals(a) || "--gender".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("Missing value for " + a); printUsage(); return; }
                gender = args[++i].toUpperCase().charAt(0);
                continue;
            }
            if ("-d".equals(a) || "--day".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("Missing value for " + a); printUsage(); return; }
                daySpec = args[++i];
                continue;
            }
            if ("-s".equals(a) || "--max-seq".equals(a)) {
                if (i + 1 >= args.length) { System.err.println("Missing value for " + a); printUsage(); return; }
                maxSeq = Integer.parseInt(args[++i]);
                continue;
            }
            // 忽略原始脚本中的线程/性能相关参数（兼容性处理）
            if ("-t".equals(a) || "--threads".equals(a)) { if (i + 1 < args.length) i++; continue; }
            if ("-p".equals(a) || "--performance".equals(a)) { continue; }
            if ("-h".equals(a) || "--help".equals(a)) { printUsage(); return; }

            // 如果不是 flag，则作为位置参数 username（目标学号）
            if (!a.startsWith("-") && username == null) {
                username = a;
                continue;
            }

            System.err.println("Unknown arg: " + a);
            printUsage();
            return;
        }

        // 如果未通过命令行提供 username，则使用 Scanner 交互读取（满足要求使用 Scanner）
        if (username == null) {
            System.out.print("请输入目标学号 (username): ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();
            if (input == null || input.trim().isEmpty()) {
                System.err.println("未提供 username，程序退出。");
                scanner.close();
                return;
            }
            username = input.trim();
            scanner.close();
        }
            if (outPath == null) outPath = "passwords_" + username + ".txt";

        char[] charset = resolveCharset(cs);

        Writer writer;
        if ("-".equals(outPath)) {
            writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        } else {
            File f = new File(outPath);
            if (f.exists() && !overwrite) {
                System.err.println("File exists. Use -o to overwrite: " + outPath);
                return;
            }
            writer = new BufferedWriter(new FileWriter(f, false));
        }

        long total = 0;
        try (BufferedWriter bw = (writer instanceof BufferedWriter) ? (BufferedWriter) writer : new BufferedWriter(writer)) {
            if (ddsssc) {
                List<Integer> days = parseDaySpec(daySpec);
                total = generateDDSSSC(bw, days, gender, startSeq, maxSeq);
            } else {
                for (int len = min; len <= max; len++) {
                    generateRecursive(bw, charset, new char[len], 0, len);
                }
            }
            bw.flush();
        }

        System.out.println("Generated " + total + " entries to " + outPath);
    }

    static List<Integer> parseDaySpec(String spec) {
        List<Integer> days = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            for (int d = 1; d <= 31; d++) days.add(d);
            return days;
        }
        String[] parts = spec.split(",");
        for (String p : parts) {
            p = p.trim();
            if (p.contains("-")) {
                String[] mm = p.split("-", 2);
                int a = Integer.parseInt(mm[0]);
                int b = Integer.parseInt(mm[1]);
                if (a > b) { int t=a; a=b; b=t; }
                for (int d = Math.max(1,a); d <= Math.min(31,b); d++) days.add(d);
            } else {
                int v = Integer.parseInt(p);
                if (v >= 1 && v <= 31) days.add(v);
            }
        }
        return days;
    }

    static long generateDDSSSC(BufferedWriter bw, List<Integer> days, char gender, int startSeq, int maxSeq) throws Exception {
        if (startSeq < 0) startSeq = 0;
        if (maxSeq > 999) maxSeq = 999;
        if (startSeq >= maxSeq) return 0L; // maxSeq 为 exclusive（与 Python range 对齐）
        long count = 0;
        for (int day : days) {
            String dayStr = String.format("%02d", day);
            String gStr = (gender == 'A') ? "ALL" : String.valueOf(gender);
            for (int seq = startSeq; seq < maxSeq; seq++) {
                // target_remainder = 1 if M else 0 (Python code used 1 for M, 0 otherwise)
                int target_remainder = (gStr.equals("M")) ? 1 : 0;
                if (gStr.equals("ALL")) target_remainder = -1; // means accept any
                if (target_remainder != -1) {
                    if (seq % 2 != target_remainder) continue;
                }
                String seqStr = String.format("%03d", seq);
                String dayKey = (gender == 'A') ? ("M_" + dayStr) : (gStr + "_" + dayStr);
                // For ALL we need both M_ and F_ entries; replicate python logic which iterates genders separately
                if (gender == 'A') {
                    // write for M and F separately
                    // For M (target_remainder 1) and F (0)
                    for (String g : new String[]{"M","F"}) {
                        int rem = g.equals("M") ? 1 : 0;
                        if (seq % 2 != rem) continue;
                        String passwordPrefix = dayStr + seqStr;
                        for (int c = 0; c <= 9; c++) {
                            String password = passwordPrefix + c;
                            bw.write(g + "_" + dayStr + "," + password);
                            bw.newLine();
                            count++;
                        }
                    }
                } else {
                    String passwordPrefix = dayStr + seqStr;
                    for (int c = 0; c <= 9; c++) {
                        String password = passwordPrefix + c;
                        bw.write(gStr + "_" + dayStr + "," + password);
                        bw.newLine();
                        count++;
                    }
                }
            }
        }
        return count;
    }

    static void generateRecursive(BufferedWriter bw, char[] charset, char[] buffer, int pos, int targetLen) throws Exception {
        if (pos == targetLen) {
            bw.write(buffer);
            bw.newLine();
            return;
        }
        for (char c : charset) {
            buffer[pos] = c;
            generateRecursive(bw, charset, buffer, pos + 1, targetLen);
        }
    }

    static char[] resolveCharset(String cs) {
        switch (cs) {
            case "lower": return "abcdefghijklmnopqrstuvwxyz".toCharArray();
            case "upper": return "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
            case "digits": return "0123456789".toCharArray();
            case "symbols": return "!@#$%^&*()-_=+[]{};:,.<>/?".toCharArray();
            case "all": return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
            default: return cs.toCharArray();
        }
    }

    static void printUsage() {
        System.out.println("Usage: java -jar wordlist-generator.jar -g -d <out> [-min N] [-max N] [-cs <charset>] [-o]\n" +
                " -g        generate mode\n" +
                " -d <path> output file path or - for stdout\n" +
                " -min N    minimum length (default 1)\n" +
                " -max N    maximum length (default 4)\n" +
                " -cs <charset> one of: lower, upper, digits, symbols, all or a literal string\n" +
                " -o        overwrite output file if exists\n");
    }
}
