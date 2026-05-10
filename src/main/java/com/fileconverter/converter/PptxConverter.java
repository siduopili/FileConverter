package com.fileconverter.converter;

import com.fileconverter.util.FileUtils;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xslf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class PptxConverter implements Converter {

    private static final Set<String> SOURCE_FORMATS = Set.of("pptx", "ppt");
    private static final Set<String> IMAGE_TARGETS = Set.of("png", "jpg", "jpeg");

    @Override public Set<String> getSourceFormats() { return SOURCE_FORMATS; }

    @Override
    public Set<String> getSupportedConversions(String sourceExtension) {
        var targets = new HashSet<>(Set.of("pdf", "txt", "png", "jpg"));
        targets.remove(sourceExtension.toLowerCase());
        return targets;
    }

    @Override
    public File convert(File sourceFile, String targetFormat, ProgressCallback callback) throws Exception {
        String tgtExt = targetFormat.toLowerCase();
        String baseName = FileUtils.stripExtension(sourceFile.getName());

        if (IMAGE_TARGETS.contains(tgtExt)) return convertToImages(sourceFile, baseName, tgtExt, callback);
        if ("pdf".equals(tgtExt)) return convertToPdf(sourceFile, baseName, callback);
        if ("txt".equals(tgtExt)) return convertToText(sourceFile, baseName, callback);
        throw new IllegalArgumentException("不支持的输出格式: " + tgtExt);
    }

    private File convertToPdf(File sourceFile, String baseName, ProgressCallback callback) throws Exception {
        callback.onProgress(5, "读取幻灯片...");
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(sourceFile))) {
            Dimension pageSize = ppt.getPageSize();
            File outputFile = new File(sourceFile.getParent(), baseName + ".pdf");

            com.lowagie.text.Document pdfDoc = new com.lowagie.text.Document(
                    new com.lowagie.text.Rectangle((float)(pageSize.width * 0.75), (float)(pageSize.height * 0.75)));
            PdfWriter.getInstance(pdfDoc, new FileOutputStream(outputFile));
            pdfDoc.open();

            var slides = ppt.getSlides();
            int total = slides.size();
            for (int i = 0; i < total; i++) {
                callback.onProgress(5 + (i * 90 / Math.max(total, 1)), "渲染第 " + (i + 1) + "/" + total + " 页");
                BufferedImage img = renderSlide(slides.get(i), pageSize);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(img, "PNG", bos);
                Image pdfImg = Image.getInstance(bos.toByteArray());
                pdfImg.scaleToFit(pdfDoc.getPageSize().getWidth() - 20, pdfDoc.getPageSize().getHeight() - 20);
                pdfDoc.add(pdfImg);
                if (i < total - 1) pdfDoc.newPage();
            }
            pdfDoc.close();
            return outputFile;
        }
    }

    private File convertToImages(File sourceFile, String baseName, String fmt, ProgressCallback callback) throws Exception {
        callback.onProgress(5, "读取幻灯片...");
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(sourceFile))) {
            Dimension pageSize = ppt.getPageSize();
            var slides = ppt.getSlides();
            int total = slides.size();

            if (total == 1) {
                File outputFile = new File(sourceFile.getParent(), baseName + "." + fmt);
                ImageIO.write(renderSlide(slides.get(0), pageSize), fmt.toUpperCase(), outputFile);
                callback.onProgress(100, "完成");
                return outputFile;
            } else {
                File dir = new File(sourceFile.getParent(), baseName + "_slides");
                Files.createDirectories(dir.toPath());
                for (int i = 0; i < total; i++) {
                    callback.onProgress(5 + (i * 90 / total), "渲染第 " + (i + 1) + "/" + total + " 页");
                    ImageIO.write(renderSlide(slides.get(i), pageSize), fmt.toUpperCase(),
                            new File(dir, String.format("slide_%03d.%s", i + 1, fmt)));
                }
                callback.onProgress(100, "完成");
                return dir;
            }
        }
    }

    private File convertToText(File sourceFile, String baseName, ProgressCallback callback) throws Exception {
        callback.onProgress(10, "提取文字...");
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(sourceFile))) {
            var slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                sb.append("=== 第 ").append(i + 1).append(" 页 ===\n");
                for (XSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof XSLFTextShape ts) sb.append(ts.getText()).append("\n");
                }
                sb.append("\n");
            }
        }
        File outputFile = new File(sourceFile.getParent(), baseName + ".txt");
        Files.writeString(outputFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
        callback.onProgress(100, "完成");
        return outputFile;
    }

    private BufferedImage renderSlide(XSLFSlide slide, Dimension size) {
        BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size.width, size.height);
        slide.draw(g);
        g.dispose();
        return img;
    }
}
