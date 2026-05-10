package com.fileconverter.ui;

import com.fileconverter.converter.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class FormatSelector extends JPanel {
    private JComboBox<String> formatCombo;
    private JTextArea infoArea;
    private Map<String, Converter> converterMap = new LinkedHashMap<>();
    private Converter currentConverter;
    private String currentSourceExt;

    private static final Map<String, String> FORMAT_NAMES = new LinkedHashMap<>();
    static {
        FORMAT_NAMES.put("png", "PNG 图片 (.png)");
        FORMAT_NAMES.put("jpg", "JPEG 图片 (.jpg)");
        FORMAT_NAMES.put("bmp", "BMP 位图 (.bmp)");
        FORMAT_NAMES.put("gif", "GIF 动图 (.gif)");
        FORMAT_NAMES.put("webp", "WebP 图片 (.webp)");
        FORMAT_NAMES.put("ico", "ICO 图标 (.ico)");
        FORMAT_NAMES.put("docx", "Word 文档 (.docx)");
        FORMAT_NAMES.put("pdf", "PDF 文档 (.pdf)");
        FORMAT_NAMES.put("txt", "纯文本 (.txt)");
        FORMAT_NAMES.put("html", "HTML 网页 (.html)");
        FORMAT_NAMES.put("md", "Markdown (.md)");
        FORMAT_NAMES.put("xlsx", "Excel 表格 (.xlsx)");
        FORMAT_NAMES.put("csv", "CSV 逗号分隔 (.csv)");
        FORMAT_NAMES.put("json", "JSON 数据 (.json)");
        FORMAT_NAMES.put("zip", "ZIP 压缩包 (.zip)");
        FORMAT_NAMES.put("tar", "TAR 归档 (.tar)");
        FORMAT_NAMES.put("tar.gz", "TAR.GZ 压缩 (.tar.gz)");
        FORMAT_NAMES.put("mp3", "MP3 音频 (.mp3)");
        FORMAT_NAMES.put("wav", "WAV 音频 (.wav)");
        FORMAT_NAMES.put("ogg", "OGG 音频 (.ogg)");
        FORMAT_NAMES.put("flac", "FLAC 无损 (.flac)");
        FORMAT_NAMES.put("mp4", "MP4 视频 (.mp4)");
        FORMAT_NAMES.put("avi", "AVI 视频 (.avi)");
        FORMAT_NAMES.put("mov", "MOV 视频 (.mov)");
        FORMAT_NAMES.put("mkv", "MKV 视频 (.mkv)");
        FORMAT_NAMES.put("webm", "WebM 视频 (.webm)");
        FORMAT_NAMES.put("pptx", "PPTX 演示 (.pptx)");
        FORMAT_NAMES.put("ppt", "PPT 演示 (.ppt)");
        FORMAT_NAMES.put("doc", "Word 97-2003 (.doc)");
        FORMAT_NAMES.put("rtf", "富文本 (.rtf)");
    }

    public FormatSelector() {
        Converter[] converters = {
                new ImageConverter(),
                new DocumentConverter(),
                new PptxConverter(),
                new SpreadsheetConverter(),
                new ArchiveConverter(),
                new MediaConverter()
        };
        for (Converter c : converters) {
            for (String fmt : c.getSourceFormats()) {
                converterMap.put(fmt, c);
            }
        }

        setLayout(new BorderLayout(0, 0));
        setOpaque(false);

        JPanel inner = new JPanel(new GridBagLayout());
        inner.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel label = new JLabel("转换为:");
        label.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        label.setForeground(new Color(60, 60, 80));
        inner.add(label, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        formatCombo = new JComboBox<>();
        formatCombo.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        formatCombo.setMaximumRowCount(20);
        formatCombo.setEnabled(false);
        inner.add(formatCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 0;
        infoArea = new JTextArea(2, 30);
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        infoArea.setForeground(new Color(100, 100, 120));
        infoArea.setOpaque(false);
        infoArea.setWrapStyleWord(true);
        infoArea.setLineWrap(true);
        infoArea.setText("请先选择或拖入一个文件");
        inner.add(infoArea, gbc);

        add(inner, BorderLayout.CENTER);
    }

    public void setSourceFile(String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        currentConverter = converterMap.get(ext);
        currentSourceExt = ext;

        formatCombo.removeAllItems();
        if (currentConverter != null) {
            Set<String> targets = currentConverter.getSupportedConversions(ext);
            for (String t : targets) {
                String name = FORMAT_NAMES.getOrDefault(t, t.toUpperCase() + " (. " + t + ")");
                formatCombo.addItem(name);
                formatCombo.putClientProperty(name, t);
            }
            formatCombo.setEnabled(true);
            infoArea.setText("源格式: " + ext.toUpperCase() + " | 可选 " + targets.size() + " 种目标格式");
        } else {
            formatCombo.addItem("(不支持此格式)");
            formatCombo.setEnabled(false);
            infoArea.setText("抱歉，暂不支持 ." + ext + " 格式的转换");
        }
    }

    public String getTargetFormat() {
        if (formatCombo.getSelectedItem() == null) return null;
        return (String) formatCombo.getClientProperty((String) formatCombo.getSelectedItem());
    }

    public Converter getConverter() {
        return currentConverter;
    }

    private String getExtension(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".tar.gz")) return "tar.gz";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
