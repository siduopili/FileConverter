package com.fileconverter.ui;

import com.fileconverter.converter.Converter;
import com.fileconverter.converter.DocumentConverter;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * 格式选择面板 — 根据源文件扩展名匹配转换器，填充目标格式下拉列表。
 * 当前只有一个 DocumentConverter，直接初始化。
 */
public class FormatSelector extends JPanel {
    private static final Map<String, Converter> CONVERTER_MAP = new LinkedHashMap<>();
    static {
        DocumentConverter docConverter = new DocumentConverter();
        for (String fmt : docConverter.getSourceFormats())
            CONVERTER_MAP.put(fmt, docConverter);
    }

    private static final Map<String, String> FORMAT_NAMES = new LinkedHashMap<>();
    static {
        FORMAT_NAMES.put("docx", "Word 文档 (.docx)");
        FORMAT_NAMES.put("doc",  "Word 97-2003 (.doc)");
        FORMAT_NAMES.put("pdf",  "PDF 文档 (.pdf)");
        FORMAT_NAMES.put("txt",  "纯文本 (.txt)");
        FORMAT_NAMES.put("html", "HTML 网页 (.html)");
        FORMAT_NAMES.put("md",   "Markdown (.md)");
        FORMAT_NAMES.put("rtf",  "富文本 (.rtf)");
    }

    private JComboBox<String> formatCombo;
    private JTextArea infoArea;
    private Converter currentConverter;

    public FormatSelector() {
        setLayout(new BorderLayout());
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
        currentConverter = CONVERTER_MAP.get(ext);

        formatCombo.removeAllItems();
        if (currentConverter != null) {
            Set<String> targets = currentConverter.getSupportedConversions(ext);
            for (String t : targets) {
                String name = FORMAT_NAMES.getOrDefault(t, t.toUpperCase() + " (." + t + ")");
                formatCombo.addItem(name);
                formatCombo.putClientProperty(name, t);
            }
            formatCombo.setEnabled(true);
            infoArea.setText("源格式: " + ext.toUpperCase()
                    + " | 可选 " + targets.size() + " 种目标格式");
        } else {
            formatCombo.addItem("(不支持此格式)");
            formatCombo.setEnabled(false);
            infoArea.setText("抱歉，暂不支持 ." + ext + " 格式的转换");
        }
    }

    public String getTargetFormat() {
        if (formatCombo.getSelectedItem() == null) return null;
        return (String) formatCombo.getClientProperty(
                (String) formatCombo.getSelectedItem());
    }

    public Converter getConverter() {
        return currentConverter;
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
