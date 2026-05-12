package com.fileconverter.ui;

import com.fileconverter.converter.Converter;
import com.fileconverter.util.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainWindow extends JFrame {

    private DefaultListModel<File> fileListModel = new DefaultListModel<>();
    private JList<File> fileList;
    private FormatSelector formatSelector;
    private JButton convertBtn, cancelBtn, removeBtn, clearBtn, pdfMergeBtn, pdfSplitBtn;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private DropPanel dropPanel;
    private SwingWorker<?, ?> currentWorker;

    public MainWindow() {
        setTitle("文档格式转换器");
        setSize(600, 620);
        setMinimumSize(new Dimension(500, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        setupUI();
        setupDropTarget();
    }

    private void setupUI() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        contentPane.setBackground(Color.WHITE);
        contentPane.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(contentPane);

        JLabel title = new JLabel("文档格式转换器", JLabel.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        title.setForeground(new Color(50, 50, 80));
        title.setBorder(new EmptyBorder(0, 0, 10, 0));
        contentPane.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // Drop area
        dropPanel = new DropPanel("拖拽文件到这里，或点击选择（支持多选）");
        dropPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        dropPanel.setMaximumSize(new Dimension(570, 100));
        dropPanel.setPreferredSize(new Dimension(570, 90));
        dropPanel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { chooseFiles(); }
            public void mouseEntered(MouseEvent e) { dropPanel.setHover(true); }
            public void mouseExited(MouseEvent e) { dropPanel.setHover(false); }
        });
        center.add(dropPanel);
        center.add(Box.createVerticalStrut(6));

        // File list
        fileList = new JList<>(fileListModel);
        fileList.setCellRenderer(new FileListRenderer());
        fileList.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        fileList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateUIState(); });
        fileList.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "delete");
        fileList.getActionMap().put("delete", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { removeSelectedFiles(); }
        });

        JScrollPane listScroll = new JScrollPane(fileList);
        listScroll.setPreferredSize(new Dimension(570, 100));
        listScroll.setMaximumSize(new Dimension(570, 150));
        listScroll.setAlignmentX(Component.CENTER_ALIGNMENT);
        listScroll.setBorder(new LineBorder(new Color(200, 200, 220)));
        center.add(listScroll);
        center.add(Box.createVerticalStrut(4));

        // Buttons row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(570, 35));

        JButton addBtn = makeSmallBtn("+ 添加", new Color(60, 160, 80));
        addBtn.addActionListener(e -> chooseFiles());
        btnRow.add(addBtn);

        removeBtn = makeSmallBtn("- 移除", new Color(200, 80, 60));
        removeBtn.addActionListener(e -> removeSelectedFiles());
        removeBtn.setEnabled(false);
        btnRow.add(removeBtn);

        clearBtn = makeSmallBtn("清空", new Color(140, 140, 160));
        clearBtn.addActionListener(e -> { fileListModel.clear(); updateUIState(); });
        clearBtn.setEnabled(false);
        btnRow.add(clearBtn);

        pdfMergeBtn = makeSmallBtn("合并PDF", new Color(220, 150, 50));
        pdfMergeBtn.addActionListener(e -> mergePDFs());
        pdfMergeBtn.setVisible(false);
        btnRow.add(pdfMergeBtn);

        pdfSplitBtn = makeSmallBtn("拆分PDF", new Color(100, 140, 200));
        pdfSplitBtn.addActionListener(e -> splitPDF());
        pdfSplitBtn.setVisible(false);
        btnRow.add(pdfSplitBtn);

        center.add(btnRow);
        center.add(Box.createVerticalStrut(6));

        // Format selector
        formatSelector = new FormatSelector();
        formatSelector.setAlignmentX(Component.CENTER_ALIGNMENT);
        formatSelector.setMaximumSize(new Dimension(570, 70));
        center.add(formatSelector);
        center.add(Box.createVerticalStrut(6));

        // Convert + Cancel buttons
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        convertBtn = new JButton("开始转换");
        convertBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        convertBtn.setForeground(Color.WHITE);
        convertBtn.setBackground(new Color(70, 130, 220));
        convertBtn.setOpaque(true);
        convertBtn.setBorderPainted(false);
        convertBtn.setFocusPainted(false);
        convertBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        convertBtn.setEnabled(false);
        convertBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (convertBtn.isEnabled()) convertBtn.setBackground(new Color(50, 110, 200)); }
            public void mouseExited(MouseEvent e) { if (convertBtn.isEnabled()) convertBtn.setBackground(new Color(70, 130, 220)); }
        });
        convertBtn.addActionListener(e -> startBatchConversion());
        actionRow.add(convertBtn);

        cancelBtn = new JButton("取消");
        cancelBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setBackground(new Color(200, 80, 60));
        cancelBtn.setOpaque(true);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.setVisible(false);
        cancelBtn.addActionListener(e -> cancelCurrentTask());
        actionRow.add(cancelBtn);

        center.add(actionRow);
        center.add(Box.createVerticalStrut(6));

        // Progress
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(570, 20));
        progressBar.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        center.add(progressBar);
        center.add(Box.createVerticalStrut(4));

        // Status
        statusLabel = new JLabel("请添加要转换的文件");
        statusLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(100, 100, 120));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(statusLabel);

        contentPane.add(center, BorderLayout.CENTER);
    }

    private JButton makeSmallBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (btn.isEnabled()) btn.setBackground(bg.darker()); }
            public void mouseExited(MouseEvent e) { if (btn.isEnabled()) btn.setBackground(bg); }
        });
        return btn;
    }

    private void setupDropTarget() {
        DropTarget dt = new DropTarget();
        dt.setComponent(this);
        try {
            dt.addDropTargetListener(new DropTargetAdapter() {
                public void dragOver(DropTargetDragEvent e) { dropPanel.setHover(true); e.acceptDrag(DnDConstants.ACTION_COPY); }
                public void dragExit(DropTargetEvent e) { dropPanel.setHover(false); }
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent e) {
                    dropPanel.setHover(false);
                    try {
                        e.acceptDrop(DnDConstants.ACTION_COPY);
                        addFiles((List<File>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                        e.dropComplete(true);
                    } catch (Exception ex) { e.dropComplete(false); showError("无法识别文件: " + ex.getMessage()); }
                }
            });
            setDropTarget(dt);
        } catch (Exception ignored) {}
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要转换的文件（可多选）");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            addFiles(List.of(chooser.getSelectedFiles()));
    }

    private void addFiles(List<File> files) {
        for (File f : files) if (!fileListModel.contains(f)) fileListModel.addElement(f);
        updateUIState();
    }

    private void removeSelectedFiles() {
        for (int i = fileList.getSelectedIndices().length - 1; i >= 0; i--)
            fileListModel.remove(fileList.getSelectedIndices()[i]);
        updateUIState();
    }

    private void updateUIState() {
        int count = fileListModel.size();
        boolean hasFiles = count > 0;
        clearBtn.setEnabled(hasFiles);
        removeBtn.setEnabled(fileList.getSelectedIndex() >= 0);

        if (hasFiles) {
            File first = fileListModel.get(0);
            formatSelector.setSourceFile(first.getName());

            boolean allPdf = true;
            for (int i = 0; i < count; i++) {
                if (!fileListModel.get(i).getName().toLowerCase().endsWith(".pdf")) { allPdf = false; break; }
            }
            pdfMergeBtn.setVisible(allPdf && count >= 2);
            pdfSplitBtn.setVisible(allPdf && count == 1);
        } else {
            pdfMergeBtn.setVisible(false);
            pdfSplitBtn.setVisible(false);
        }
        convertBtn.setEnabled(hasFiles && formatSelector.getTargetFormat() != null);
        if (hasFiles)
            convertBtn.setText(count > 1 ? "批量转换 (" + count + " 个)" : "开始转换");

        statusLabel.setText(hasFiles ? "已添加 " + count + " 个文件" : "请添加要转换的文件");
        statusLabel.setForeground(new Color(100, 100, 120));
    }

    // ---- Cancel ----

    private void cancelCurrentTask() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            statusLabel.setText("已取消");
            statusLabel.setForeground(new Color(200, 120, 20));
        }
        resetUI();
    }

    private void resetUI() {
        convertBtn.setEnabled(fileListModel.size() > 0);
        cancelBtn.setVisible(false);
        progressBar.setVisible(false);
        progressBar.setValue(0);
    }

    // ---- PDF tools ----

    private void mergePDFs() {
        if (fileListModel.size() < 2) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("保存合并后的 PDF");
        chooser.setSelectedFile(new File("merged.pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File output = chooser.getSelectedFile();
        startTask();

        currentWorker = new SwingWorker<File, Integer>() {
            protected File doInBackground() throws Exception {
                PDFMergerUtility merger = new PDFMergerUtility();
                merger.setDestinationFileName(output.getAbsolutePath());
                for (int i = 0; i < fileListModel.size() && !isCancelled(); i++) {
                    merger.addSource(fileListModel.get(i));
                    setProgress((i + 1) * 100 / fileListModel.size());
                    publish(i + 1);
                }
                if (!isCancelled()) merger.mergeDocuments(null);
                return output;
            }
            protected void process(List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                progressBar.setValue(done * 100 / fileListModel.size());
                statusLabel.setText("合并中: " + done + "/" + fileListModel.size());
            }
            protected void done() {
                try {
                    if (isCancelled()) return;
                    File result = get();
                    statusLabel.setText("合并完成: " + result.getName() + " (" + FileUtils.formatSize(result.length()) + ")");
                    statusLabel.setForeground(new Color(40, 140, 60));
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(result.getParentFile());
                } catch (Exception e) {
                    statusLabel.setText("合并失败: " + e.getMessage());
                    statusLabel.setForeground(new Color(200, 60, 60));
                }
                resetUI();
            }
        };
        currentWorker.execute();
    }

    private void splitPDF() {
        if (fileListModel.size() != 1) return;
        String input = JOptionPane.showInputDialog(this,
                "输入页码范围（如: 1-3,5,7-9）:", "拆分PDF", JOptionPane.QUESTION_MESSAGE);
        if (input == null || input.isBlank()) return;

        File source = fileListModel.get(0);
        startTask();

        currentWorker = new SwingWorker<File, Void>() {
            protected File doInBackground() throws Exception {
                try (var doc = Loader.loadPDF(source)) {
                    List<Integer> pages = parsePages(input, doc.getNumberOfPages());
                    var newDoc = new org.apache.pdfbox.pdmodel.PDDocument();
                    for (int p : pages) {
                        if (isCancelled()) break;
                        newDoc.addPage(doc.getPage(p - 1));
                    }
                    File out = new File(source.getParent(), source.getName().replace(".pdf", "_extract.pdf"));
                    if (!isCancelled()) { newDoc.save(out); newDoc.close(); return out; }
                    newDoc.close();
                    return null;
                }
            }
            protected void done() {
                try {
                    if (isCancelled()) return;
                    File result = get();
                    if (result != null) {
                        statusLabel.setText("拆分完成: " + result.getName());
                        statusLabel.setForeground(new Color(40, 140, 60));
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(result.getParentFile());
                    }
                } catch (Exception e) {
                    statusLabel.setText("拆分失败: " + e.getMessage());
                    statusLabel.setForeground(new Color(200, 60, 60));
                }
                resetUI();
            }
        };
        currentWorker.execute();
    }

    private List<Integer> parsePages(String input, int total) {
        List<Integer> pages = new ArrayList<>();
        for (String part : input.split(",")) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                int s = Integer.parseInt(range[0].trim()), e = Integer.parseInt(range[1].trim());
                for (int i = s; i <= Math.min(e, total); i++) pages.add(i);
            } else {
                int p = Integer.parseInt(part);
                if (p <= total) pages.add(p);
            }
        }
        return pages;
    }

    // ---- Batch conversion ----

    private void startBatchConversion() {
        if (fileListModel.isEmpty()) { showError("请先添加文件"); return; }

        String targetFormat = formatSelector.getTargetFormat();
        Converter converter = formatSelector.getConverter();
        if (targetFormat == null || converter == null) {
            showError("当前文件格式暂不支持转换"); return;
        }

        // Validate all files match the converter
        List<File> valid = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (int i = 0; i < fileListModel.size(); i++) {
            File f = fileListModel.get(i);
            String ext = FileUtils.getExtension(f);
            if (converter.getSourceFormats().contains(ext)) {
                valid.add(f);
            } else {
                skipped.add(f.getName() + " (." + ext + ")");
            }
        }

        if (valid.isEmpty()) {
            showError("没有文件匹配当前转换器。请确认文件格式。" +
                    (skipped.isEmpty() ? "" : "\n已跳过: " + String.join(", ", skipped)));
            return;
        }

        if (!skipped.isEmpty()) {
            String msg = "以下文件格式不匹配当前转换器，将被跳过:\n" + String.join("\n", skipped) +
                    "\n\n继续转换 " + valid.size() + " 个匹配的文件?";
            if (JOptionPane.showConfirmDialog(this, msg, "格式不匹配", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
                return;
        }

        startTask();

        currentWorker = new SwingWorker<Void, String>() {
            int success = 0, fail = 0;
            StringBuilder errors = new StringBuilder();

            protected Void doInBackground() {
                for (int i = 0; i < valid.size() && !isCancelled(); i++) {
                    File f = valid.get(i);
                    int pct = i * 100 / valid.size();
                    setProgress(pct);
                    publish("[" + (i + 1) + "/" + valid.size() + "] " + f.getName());

                    try {
                        converter.convert(f, targetFormat, (p, m) -> {});
                        success++;
                    } catch (Exception e) {
                        fail++;
                        errors.append(f.getName()).append(": ").append(e.getMessage()).append("\n");
                    }
                }
                return null;
            }
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                    setProgress(getProgress());
                    progressBar.setValue(getProgress());
                }
            }
            protected void done() {
                if (isCancelled()) { resetUI(); return; }
                progressBar.setValue(100);
                String result = "完成: " + success + " 成功";
                if (fail > 0) result += ", " + fail + " 失败";
                statusLabel.setText(result);
                statusLabel.setForeground(fail == 0 ? new Color(40, 140, 60) : new Color(200, 120, 20));
                if (fail > 0)
                    JOptionPane.showMessageDialog(MainWindow.this, result + "\n\n失败详情:\n" + errors,
                            "批量转换结果", JOptionPane.WARNING_MESSAGE);
                resetUI();
            }
        };
        currentWorker.execute();
    }

    private void startTask() {
        convertBtn.setEnabled(false);
        cancelBtn.setVisible(true);
        progressBar.setVisible(true);
        progressBar.setValue(0);
    }

    // ---- Helpers ----

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
    }

    // ---- File list renderer ----

    static class FileListRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            if (value instanceof File f) {
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                String ext = dot >= 0 ? name.substring(dot + 1).toUpperCase() : "";
                label.setText(String.format("%s  [%s]  %s", name, ext, FileUtils.formatSize(f.length())));
                label.setIcon(UIManager.getIcon("FileView.fileIcon"));
            }
            return label;
        }
    }
}
