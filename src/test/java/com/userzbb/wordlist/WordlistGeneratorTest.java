package com.userzbb.wordlist;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WordlistGeneratorTest {

    @Test
    public void smallDdssscSampleMatchesPython() throws Exception {
        // 使用 Java 工具生成小样例，并与预期结果逐行比对
        String username = "testuser";
        String[] args = new String[]{username, "-g", "M", "-d", "08", "-s", "4"};
        // 删除旧文件（如存在）
        File out = Path.of(System.getProperty("user.dir"), "passwords_" + username + ".txt").toFile();
        if (out.exists()) out.delete();
        WordlistGenerator.main(args);

        // 读取生成文件
        List<String> javaLines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(out))) {
            String l; while ((l = r.readLine()) != null) javaLines.add(l.trim());
        }

        // 构建期望结果（day=08, gender=M, max_seq=4 -> 仅 seq=1,3）
        List<String> expected = new ArrayList<>();
        String day = "08";
        for (int seq = 0; seq < 4; seq++) {
            if (seq % 2 == 0) continue; // 男生：顺序码为奇数
            String sss = String.format("%03d", seq);
            for (int c = 0; c <= 9; c++) {
                String pwd = day + sss + c;
                expected.add("M_" + day + "," + pwd);
            }
        }

        // 断言数量与内容一致
        assertEquals(expected.size(), javaLines.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), javaLines.get(i));
        }

        // 清理临时文件
        out.delete();
    }
}
