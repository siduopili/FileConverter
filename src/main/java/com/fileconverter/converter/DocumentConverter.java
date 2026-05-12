package com.fileconverter.converter;

import com.fileconverter.util.FileUtils;
import com.fileconverter.util.PdfFontUtils;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.text.rtf.RTFEditorKit;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 文档格式转换器 — 支持 DOCX / DOC / PDF / TXT / HTML / MD / RTF 之间的互转。
 *
 * 设计思路：两阶段模型
 *   1. 提取 —— 从源文件提取纯文本（DOCX→PDF 除外，走 POI 手工渲染保留排版）
 *   2. 写入 —— 将文本写入目标格式
 */
public class DocumentConverter implements Converter {

    private static final Set<String> SOURCE_FORMATS =
            Set.of("docx", "doc", "pdf", "txt", "html", "md", "markdown", "rtf");

    private static final Parser MD_PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

    // ── Converter 接口 ──────────────────────────────────────────

    @Override public Set<String> getSourceFormats() { return SOURCE_FORMATS; }

    @Override
    public Set<String> getSupportedConversions(String sourceExtension) {
        var targets = new HashSet<>(Set.of("docx", "pdf", "txt", "html", "md"));
        targets.remove(sourceExtension.toLowerCase());
        return targets;
    }

    @Override
    public File convert(File sourceFile, String targetFormat, ProgressCallback callback)
            throws Exception {
        String srcExt = FileUtils.getExtension(sourceFile);
        String tgtExt = targetFormat.toLowerCase();
        File outputFile = new File(sourceFile.getParent(),
                FileUtils.stripExtension(sourceFile.getName()) + "." + tgtExt);

        // DOCX → PDF：用 POI 手工渲染以保留排版（段落、表格、图片）
        if ("docx".equals(srcExt) && "pdf".equals(tgtExt)) {
            callback.onProgress(5, "读取 DOCX 文档...");
            return convertDocxToPdf(sourceFile, outputFile, callback);
        }

        // 通用路径：提取文本 → 写入目标格式
        callback.onProgress(10, "读取文档...");
        String text = extractText(sourceFile, srcExt);

        callback.onProgress(50, "生成 " + tgtExt.toUpperCase() + " ...");
        writeTo(text, tgtExt, outputFile);

        callback.onProgress(100, "完成");
        return outputFile;
    }

    // ── DOCX → PDF 手工渲染 ─────────────────────────────────────

    private File convertDocxToPdf(File sourceFile, File outputFile, ProgressCallback callback)
            throws Exception {
        com.lowagie.text.Document pdfDoc = new com.lowagie.text.Document();
        PdfWriter.getInstance(pdfDoc, new FileOutputStream(outputFile));
        pdfDoc.open();

        try (XWPFDocument docx = new XWPFDocument(new FileInputStream(sourceFile))) {
            List<IBodyElement> elements = docx.getBodyElements();
            int total = elements.size();
            for (int i = 0; i < total; i++) {
                callback.onProgress(10 + (i * 80 / Math.max(total, 1)),
                        "渲染元素 " + (i + 1) + "/" + total);
                IBodyElement el = elements.get(i);
                if (el instanceof XWPFParagraph para) {
                    renderParagraph(para, pdfDoc);
                } else if (el instanceof XWPFTable table) {
                    renderTable(table, pdfDoc);
                }
            }
        }
        pdfDoc.close();
        callback.onProgress(100, "完成（POI 渲染）");
        return outputFile;
    }

    private void renderParagraph(XWPFParagraph para, com.lowagie.text.Document pdfDoc)
            throws Exception {
        boolean isHeading = para.getStyle() != null && para.getStyle().startsWith("Heading");

        StringBuilder buf = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            // 输出缓冲区文本
            if (!buf.isEmpty()) {
                pdfDoc.add(new Paragraph(buf.toString(),
                        isHeading ? PdfFontUtils.getBoldFont() : PdfFontUtils.getNormalFont()));
                buf.setLength(0);
            }
            // 嵌入图片
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                try {
                    Image img = Image.getInstance(pic.getPictureData().getData());
                    float maxW = pdfDoc.getPageSize().getWidth()
                            - pdfDoc.leftMargin() - pdfDoc.rightMargin();
                    if (img.getWidth() > maxW)
                        img.scaleToFit(maxW, pdfDoc.getPageSize().getHeight() - 60);
                    pdfDoc.add(img);
                } catch (Exception e) {
                    pdfDoc.add(new Paragraph("[图片]", PdfFontUtils.getNormalFont()));
                }
            }
            String t = run.getText(0);
            if (t != null && !t.isEmpty()) buf.append(t);
        }
        // 输出剩余文本
        if (!buf.isEmpty()) {
            pdfDoc.add(new Paragraph(buf.toString(),
                    isHeading ? PdfFontUtils.getBoldFont() : PdfFontUtils.getNormalFont()));
        }
        // 空段落占位
        if (buf.isEmpty() && para.getRuns().isEmpty()) {
            pdfDoc.add(new Paragraph(" ", PdfFontUtils.getNormalFont()));
        }
    }

    private void renderTable(XWPFTable table, com.lowagie.text.Document pdfDoc)
            throws Exception {
        int cols = 0;
        for (XWPFTableRow row : table.getRows())
            cols = Math.max(cols, row.getTableCells().size());
        if (cols == 0) return;

        com.lowagie.text.pdf.PdfPTable pdfTable =
                new com.lowagie.text.pdf.PdfPTable(cols);
        pdfTable.setWidthPercentage(100);

        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder ct = new StringBuilder();
                for (XWPFParagraph para : cell.getParagraphs()) {
                    if (!ct.isEmpty()) ct.append("\n");
                    ct.append(para.getText());
                }
                pdfTable.addCell(new Paragraph(ct.toString(), PdfFontUtils.getNormalFont()));
            }
        }
        pdfDoc.add(pdfTable);
        pdfDoc.add(new Paragraph(" ", PdfFontUtils.getNormalFont()));
    }

    // ── 文本提取（各源格式 → 纯文本）────────────────────────────

    private String extractText(File file, String ext) throws Exception {
        return switch (ext) {
            case "docx"        -> extractDocx(file);
            case "doc"         -> extractDoc(file);
            case "pdf"         -> extractPdf(file);
            case "txt"         -> FileUtils.readWithEncodingDetection(file);
            case "html"        -> extractHtml(file);
            case "md", "markdown" -> FileUtils.readWithEncodingDetection(file);
            case "rtf"         -> extractRtf(file);
            default -> throw new IllegalArgumentException("不支持的输入格式: " + ext);
        };
    }

    private String extractDocx(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                String style = para.getStyle(), text = para.getText();
                if (text == null || text.isBlank()) {
                    sb.append("\n"); continue;
                }
                if (style != null && style.startsWith("Heading")) {
                    int level = Integer.parseInt(style.replace("Heading", "").trim());
                    sb.append("#".repeat(level)).append(" ").append(text).append("\n\n");
                } else {
                    sb.append(text).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private String extractDoc(File file) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(new FileInputStream(file))) {
            return new WordExtractor(doc).getText();
        }
    }

    private String extractPdf(File file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractHtml(File file) throws Exception {
        String html = FileUtils.readWithEncodingDetection(file);
        // 检测并处理声明的字符集
        String lower = html.toLowerCase();
        int ci = lower.indexOf("charset=");
        if (ci >= 0) {
            int end = lower.indexOf("\"", ci);
            if (end < 0) end = lower.indexOf("'", ci);
            if (end < 0) end = lower.indexOf(">", ci);
            if (end > ci) {
                String declared = lower.substring(ci + 8, end).trim();
                if (!declared.contains("utf-8") && !declared.contains("utf8")) {
                    try { html = Files.readString(file.toPath(),
                            Charset.forName(declared.toUpperCase())); }
                    catch (Exception ignored) {}
                }
            }
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").trim();
    }

    private String extractRtf(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            RTFEditorKit kit = new RTFEditorKit();
            javax.swing.text.Document doc = kit.createDefaultDocument();
            kit.read(fis, doc, 0);
            return doc.getText(0, doc.getLength());
        }
    }

    // ── 目标格式写入（纯文本 → 各格式）──────────────────────────

    private void writeTo(String text, String format, File file) throws Exception {
        switch (format) {
            case "txt"  -> Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            case "html" -> writeHtml(text, file);
            case "md"   -> Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            case "pdf"  -> writePdf(text, file);
            case "docx" -> writeDocx(text, file);
            default -> throw new IllegalArgumentException("不支持的输出格式: " + format);
        }
    }

    private void writePdf(String text, File file) throws Exception {
        com.lowagie.text.Document pdfDoc = new com.lowagie.text.Document();
        PdfWriter.getInstance(pdfDoc, new FileOutputStream(file));
        pdfDoc.open();
        for (String line : text.split("\n")) {
            pdfDoc.add(new Paragraph(line.isBlank() ? " " : line,
                    PdfFontUtils.getNormalFont()));
        }
        pdfDoc.close();
    }

    private void writeHtml(String text, File file) throws Exception {
        String body = text.lines()
                .map(line -> line.isBlank() ? "<br>" :
                        "<p>" + escapeHtml(line) + "</p>")
                .reduce("", (a, b) -> a + "\n" + b);
        Files.writeString(file.toPath(),
                "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">" +
                "<title>Document</title></head><body>" + body +
                "</body></html>", StandardCharsets.UTF_8);
    }

    private void writeDocx(String text, File file) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String line : text.split("\n")) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line);
                run.setFontSize(11);
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                doc.write(fos);
            }
        }
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
