package com.fileconverter.util;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;

public final class FileUtils {
    private FileUtils() {}

    public static String getExtension(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".tar.gz")) return "tar.gz";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    public static String stripExtension(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".tar.gz")) return name.substring(0, name.length() - 7);
        if (lower.endsWith(".tgz")) return name.substring(0, name.length() - 4);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Auto-detect encoding: try UTF-8 first, fallback to GBK if garbled */
    public static String readWithEncodingDetection(File file) throws IOException {
        byte[] raw = Files.readAllBytes(file.toPath());
        if (raw.length == 0) return "";
        String utf8 = new String(raw, StandardCharsets.UTF_8);
        if (utf8.indexOf('�') >= 0) {
            String gbk = new String(raw, Charset.forName("GBK"));
            if (gbk.indexOf('�') < 0) return gbk;
        }
        if (looksLikeGarbledUtf8(utf8, raw)) {
            return new String(raw, Charset.forName("GBK"));
        }
        return utf8;
    }

    private static boolean looksLikeGarbledUtf8(String text, byte[] raw) {
        if (raw.length > 0 && (raw[0] & 0x80) != 0) {
            long chineseChars = text.codePoints()
                .filter(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                .count();
            int len = Math.min(text.length(), 100);
            long suspicious = text.substring(0, len).codePoints()
                .filter(c -> c < 0x80 && c > 0x7E).limit(20).count();
            return chineseChars == 0 && (suspicious > 5 || text.length() < raw.length / 2);
        }
        return false;
    }
}
