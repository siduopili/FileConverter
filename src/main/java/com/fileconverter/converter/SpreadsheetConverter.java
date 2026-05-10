package com.fileconverter.converter;

import com.fileconverter.util.FileUtils;
import com.fileconverter.util.PdfFontUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.RGBColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class SpreadsheetConverter implements Converter {

    private static final Set<String> SOURCE_FORMATS = Set.of("xlsx", "xls", "csv", "json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public Set<String> getSourceFormats() { return SOURCE_FORMATS; }

    @Override
    public Set<String> getSupportedConversions(String sourceExtension) {
        var targets = new HashSet<>(Set.of("xlsx", "csv", "json", "pdf"));
        targets.remove(sourceExtension.toLowerCase());
        return targets;
    }

    @Override
    public File convert(File sourceFile, String targetFormat, ProgressCallback callback) throws Exception {
        String srcExt = FileUtils.getExtension(sourceFile);
        String tgtExt = targetFormat.toLowerCase();
        String baseName = FileUtils.stripExtension(sourceFile.getName());
        File outputFile = new File(sourceFile.getParent(), baseName + "." + tgtExt);

        if (("xlsx".equals(srcExt) || "xls".equals(srcExt)) && "pdf".equals(tgtExt)) {
            return convertToPdf(sourceFile, outputFile, callback);
        }

        callback.onProgress(10, "读取表格数据...");
        List<List<String>> data = readData(sourceFile, srcExt);
        if (data.isEmpty()) throw new IOException("表格无数据");

        callback.onProgress(50, "转换格式...");
        switch (tgtExt) {
            case "xlsx" -> writeXlsx(data, outputFile);
            case "csv"  -> writeCsv(data, outputFile);
            case "json" -> writeJson(data, outputFile);
            default -> throw new IllegalArgumentException("不支持的输出格式: " + tgtExt);
        }
        callback.onProgress(100, "完成");
        return outputFile;
    }

    private File convertToPdf(File sourceFile, File outputFile, ProgressCallback callback) throws Exception {
        callback.onProgress(10, "读取工作簿...");
        try (Workbook wb = WorkbookFactory.create(sourceFile)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            int maxCols = 0;
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row != null) maxCols = Math.max(maxCols, row.getLastCellNum());
            }
            if (maxCols == 0) throw new IOException("表格无数据");

            // Collect merged regions
            Set<String> mergedCells = new HashSet<>();
            for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                CellRangeAddress region = sheet.getMergedRegion(i);
                for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                    for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                        if (r != region.getFirstRow() || c != region.getFirstColumn()) {
                            mergedCells.add(r + "," + c);
                        }
                    }
                }
            }

            callback.onProgress(30, "生成PDF...");
            com.lowagie.text.Document pdfDoc = new com.lowagie.text.Document(
                    com.lowagie.text.PageSize.A4.rotate());
            PdfWriter.getInstance(pdfDoc, new FileOutputStream(outputFile));
            pdfDoc.open();

            PdfPTable pdfTable = new PdfPTable(maxCols);
            pdfTable.setWidthPercentage(100);

            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                for (int c = 0; c < maxCols; c++) {
                    // Skip merged cells (only render the top-left cell)
                    if (mergedCells.contains(r + "," + c)) continue;

                    Cell cell = row != null ? row.getCell(c) : null;
                    String text = cell != null ? getCellString(cell) : "";

                    PdfPCell pdfCell = new PdfPCell(new Paragraph(text, PdfFontUtils.createFont(8)));
                    pdfCell.setPadding(3);

                    // Check for merged region (this cell is the anchor)
                    if (cell != null) {
                        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                            CellRangeAddress region = sheet.getMergedRegion(i);
                            if (region.getFirstRow() == r && region.getFirstColumn() == c) {
                                pdfCell.setRowspan(region.getLastRow() - region.getFirstRow() + 1);
                                pdfCell.setColspan(region.getLastColumn() - region.getFirstColumn() + 1);
                                break;
                            }
                        }
                        CellStyle style = cell.getCellStyle();
                        if (style.getBorderTop() != BorderStyle.NONE) pdfCell.setBorderWidthTop(0.5f);
                        if (style.getBorderBottom() != BorderStyle.NONE) pdfCell.setBorderWidthBottom(0.5f);
                    }

                    if (r == 0) pdfCell.setBackgroundColor(new RGBColor(220, 220, 230));

                    pdfTable.addCell(pdfCell);
                }
                callback.onProgress(30 + (r * 60 / Math.max(lastRow, 1)), "写入第 " + (r + 1) + " 行");
            }
            pdfDoc.add(pdfTable);
            pdfDoc.close();
        }
        callback.onProgress(100, "完成");
        return outputFile;
    }

    // ---- Data reading ----

    private List<List<String>> readData(File file, String ext) throws Exception {
        return switch (ext) {
            case "xlsx", "xls" -> readExcel(file);
            case "csv" -> readCsv(file);
            case "json" -> readJson(file);
            default -> throw new IllegalArgumentException("不支持的输入格式: " + ext);
        };
    }

    private List<List<String>> readExcel(File file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    cells.add(getCellString(row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private String getCellString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) yield cell.getLocalDateTimeCellValue().toString();
                double v = cell.getNumericCellValue();
                yield v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private List<List<String>> readCsv(File file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        byte[] raw = Files.readAllBytes(file.toPath());
        if (raw.length == 0) return rows;
        String content = new String(raw, StandardCharsets.UTF_8);
        if (content.indexOf('�') >= 0) content = new String(raw, Charset.forName("GBK"));
        for (String line : content.split("\n")) {
            line = line.replace("\r", "");
            rows.add(parseCsvLine(line));
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else inQuotes = false;
                } else sb.append(c);
            } else {
                if (c == '"') inQuotes = true;
                else if (c == ',') { fields.add(sb.toString().trim()); sb.setLength(0); }
                else sb.append(c);
            }
        }
        fields.add(sb.toString().trim());
        return fields;
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> readJson(File file) throws Exception {
        byte[] raw = Files.readAllBytes(file.toPath());
        String json = new String(raw, StandardCharsets.UTF_8);
        if (json.indexOf('�') >= 0) json = new String(raw, Charset.forName("GBK"));
        try {
            List<List<Object>> parsed = GSON.fromJson(json, new TypeToken<List<List<Object>>>(){}.getType());
            if (parsed != null && !parsed.isEmpty())
                return parsed.stream().map(r -> r.stream().map(o -> o == null ? "" : o.toString()).toList()).toList();
        } catch (Exception ignored) {}
        try {
            List<Map<String, Object>> parsed = GSON.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());
            if (parsed != null && !parsed.isEmpty()) {
                Set<String> keys = new LinkedHashSet<>(parsed.get(0).keySet());
                List<List<String>> rows = new ArrayList<>();
                rows.add(new ArrayList<>(keys));
                for (var obj : parsed) {
                    List<String> row = new ArrayList<>();
                    for (String key : keys) { Object v = obj.get(key); row.add(v == null ? "" : v.toString()); }
                    rows.add(row);
                }
                return rows;
            }
        } catch (Exception ignored) {}
        throw new IOException("无法解析JSON表格数据，请使用二维数组或对象数组格式");
    }

    // ---- Data writing ----

    private void writeXlsx(List<List<String>> data, File file) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < data.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = data.get(r);
                for (int c = 0; c < cells.size(); c++) row.createCell(c).setCellValue(cells.get(c));
            }
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
    }

    private void writeCsv(List<List<String>> data, File file) throws Exception {
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            for (List<String> row : data)
                pw.println(String.join(",", row.stream().map(this::escapeCsv).toList()));
        }
    }

    private String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private void writeJson(List<List<String>> data, File file) throws Exception {
        if (data.size() <= 1) {
            Files.writeString(file.toPath(), GSON.toJson(data), StandardCharsets.UTF_8);
        } else {
            List<String> headers = data.get(0);
            List<Map<String, String>> objects = new ArrayList<>();
            for (int i = 1; i < data.size(); i++) {
                Map<String, String> obj = new LinkedHashMap<>();
                List<String> row = data.get(i);
                for (int j = 0; j < headers.size(); j++)
                    obj.put(headers.get(j), j < row.size() ? row.get(j) : "");
                objects.add(obj);
            }
            Files.writeString(file.toPath(), GSON.toJson(objects), StandardCharsets.UTF_8);
        }
    }
}
