package com.fileconverter.util;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;

public final class FileUtils {
    private FileUtils() {}

    /** 获取文件扩展名（小写），不含点号 */
    public static String getExtension(File file) {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    /** 去除文件名中的扩展名 */
    public static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** 格式化文件大小为可读字符串 */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 自动检测编码读取文本文件。
     * 优先 UTF-8，若出现乱码特征则回退到 GBK（Windows 中文环境常用编码）。
     */
    public static String readWithEncodingDetection(File file) throws IOException {
        byte[] raw = Files.readAllBytes(file.toPath());
        if (raw.length == 0) return "";

        // 先尝试 UTF-8
        String utf8 = new String(raw, StandardCharsets.UTF_8);
        if (utf8.indexOf('�') >= 0) {
            // 替换字符出现 → 可能不是 UTF-8，试 GBK
            String gbk = new String(raw, Charset.forName("GBK"));
            if (gbk.indexOf('�') < 0) return gbk;
        }
        // 启发式检测
        if (looksLikeGarbledUtf8(utf8, raw))
            return new String(raw, Charset.forName("GBK"));
        return utf8;
    }

    /** 启发式判断 UTF-8 解码结果是否乱码 */
    private static boolean looksLikeGarbledUtf8(String text, byte[] raw) {
        if (raw.length > 0 && (raw[0] & 0x80) != 0) {
            long chineseChars = text.codePoints()
                .filter(c -> Character.UnicodeBlock.of(c)
                        == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                .count();
            int len = Math.min(text.length(), 100);
            long suspicious = text.substring(0, len).codePoints()
                .filter(c -> c < 0x80 && c > 0x7E).limit(20).count();
            return chineseChars == 0
                    && (suspicious > 5 || text.length() < raw.length / 2);
        }
        return false;
    }
}
