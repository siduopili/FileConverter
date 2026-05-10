package com.fileconverter.converter;

import com.fileconverter.util.FileUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class ImageConverter implements Converter {

    private static final Set<String> SOURCE_FORMATS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "webp", "ico", "tiff", "tif");

    private static final Map<String, String> FORMAT_MAP = Map.of(
            "jpg", "JPEG", "jpeg", "JPEG", "png", "PNG", "bmp", "BMP",
            "gif", "GIF", "ico", "ICO", "tiff", "TIFF", "tif", "TIFF"
    );

    private static final Set<String> WRITER_UNSUPPORTED = Set.of("webp", "ico", "tiff", "tif");

    @Override
    public Set<String> getSourceFormats() { return SOURCE_FORMATS; }

    @Override
    public Set<String> getSupportedConversions(String sourceExtension) {
        var formats = new HashSet<>(FORMAT_MAP.keySet());
        formats.remove(sourceExtension.toLowerCase());
        return formats;
    }

    @Override
    public File convert(File sourceFile, String targetFormat, ProgressCallback callback) throws Exception {
        String tgtExt = targetFormat.toLowerCase();

        callback.onProgress(10, "读取图片...");
        BufferedImage image = ImageIO.read(sourceFile);
        if (image == null) throw new IOException("无法读取图片: " + sourceFile.getName());

        callback.onProgress(30, "处理图片...");
        if ("ico".equals(tgtExt)) {
            image = resizeForIcon(image, 256);
        }

        callback.onProgress(60, "写入中...");
        String baseName = FileUtils.stripExtension(sourceFile.getName());
        File outputFile = new File(sourceFile.getParent(), baseName + "." + tgtExt);

        if ("webp".equals(tgtExt)) {
            writeWebP(image, outputFile);
        } else if ("GIF".equals(tgtExt.toUpperCase())) {
            writeGif(image, outputFile);
        } else if (WRITER_UNSUPPORTED.contains(tgtExt)) {
            writeWithFormat(image, tgtExt, outputFile);
        } else {
            String formatName = FORMAT_MAP.getOrDefault(tgtExt, tgtExt.toUpperCase());
            if (!ImageIO.write(image, formatName, outputFile)) {
                throw new IOException("不支持写入 " + tgtExt.toUpperCase() + " 格式");
            }
        }

        callback.onProgress(100, "完成");
        return outputFile;
    }

    private void writeWebP(BufferedImage image, File outputFile) throws IOException {
        // Try TwelveMonkeys WebP writer
        if (!ImageIO.write(image, "webp", outputFile)) {
            // Try built-in format name variations
            if (!ImageIO.write(image, "WebP", outputFile)) {
                throw new IOException("WebP 写入需要额外编码器。建议安装 webp-imageio 库，或使用 PNG 代替。");
            }
        }
    }

    private void writeWithFormat(BufferedImage image, String format, File outputFile) throws IOException {
        // Try the mapped name first, then fallback
        String mappedName = FORMAT_MAP.get(format);
        if (mappedName != null && ImageIO.write(image, mappedName, outputFile)) return;
        if (ImageIO.write(image, format, outputFile)) return;
        throw new IOException("不支持写入 " + format.toUpperCase() + " 格式。TwelveMonkeys 库可能只提供读取支持。");
    }

    private BufferedImage resizeForIcon(BufferedImage img, int maxSize) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= maxSize && h <= maxSize) return img;
        double scale = (double) maxSize / Math.max(w, h);
        int nw = (int)(w * scale), nh = (int)(h * scale);
        Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return result;
    }

    private void writeGif(BufferedImage image, File outputFile) throws IOException {
        BufferedImage indexed = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D g = indexed.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        ImageIO.write(indexed, "GIF", outputFile);
    }
}
