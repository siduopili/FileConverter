package com.fileconverter.util;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;

import java.io.IOException;

public final class PdfFontUtils {
    private PdfFontUtils() {}

    private static final Object LOCK = new Object();
    private static volatile BaseFont baseFont;
    private static volatile Font normalFont;
    private static volatile Font boldFont;

    private static final String[] FONT_PATHS = {
        "C:\\Windows\\Fonts\\msyh.ttc,0",
        "C:\\Windows\\Fonts\\simsun.ttc,0",
        "C:\\Windows\\Fonts\\simhei.ttf",
        "C:\\Windows\\Fonts\\msyhbd.ttc,0",
        "/System/Library/Fonts/PingFang.ttc,0",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
    };

    public static BaseFont getBaseFont() throws IOException {
        if (baseFont != null) return baseFont;
        synchronized (LOCK) {
            if (baseFont != null) return baseFont;
            for (String path : FONT_PATHS) {
                try {
                    baseFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return baseFont;
                } catch (Exception ignored) {}
            }
            // Fallback: try without embedding
            for (String path : FONT_PATHS) {
                try {
                    baseFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                    return baseFont;
                } catch (Exception ignored) {}
            }
        }
        throw new IOException("找不到中文字体，请安装微软雅黑或宋体");
    }

    public static Font getNormalFont() throws IOException {
        if (normalFont != null) return normalFont;
        synchronized (LOCK) {
            if (normalFont != null) return normalFont;
            normalFont = new Font(getBaseFont(), 12);
            return normalFont;
        }
    }

    public static Font getBoldFont() throws IOException {
        if (boldFont != null) return boldFont;
        synchronized (LOCK) {
            if (boldFont != null) return boldFont;
            boldFont = new Font(getBaseFont(), 14, Font.BOLD);
            return boldFont;
        }
    }

    /** Create a font with specific size */
    public static Font createFont(float size) throws IOException {
        return new Font(getBaseFont(), size);
    }

    /** Create a font with specific size and style */
    public static Font createFont(float size, int style) throws IOException {
        return new Font(getBaseFont(), size, style);
    }

    /** Reset cached fonts (for testing or font path changes) */
    public static void reset() {
        synchronized (LOCK) {
            baseFont = null;
            normalFont = null;
            boldFont = null;
        }
    }
}
