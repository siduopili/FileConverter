package com.fileconverter;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.apache.xmlbeans.XmlCursor;

import java.io.*;
import java.nio.file.*;

public class ResumePhotoAdder {
    public static void main(String[] args) throws Exception {
        String docPath = "C:\\Users\\Administrator\\Desktop\\李志刚_优化版.docx";
        String imgPath = "C:\\Users\\Administrator\\Desktop\\微信图片_20260415231330_388_41.jpg";

        byte[] imgBytes = Files.readAllBytes(Path.of(imgPath));
        String imgType = imgPath.toLowerCase().endsWith(".png") ? "PNG" : "JPEG";

        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(docPath))) {

            // --- Ensure tight page margins and A4 size for single page ---
            CTDocument1 ctDoc = doc.getDocument();
            CTBody body = ctDoc.getBody();
            CTSectPr sectPr = body.getSectPr();
            if (sectPr == null) {
                sectPr = body.addNewSectPr();
            }
            // A4 page size
            CTPageSz pgSz = sectPr.getPgSz();
            if (pgSz == null) pgSz = sectPr.addNewPgSz();
            pgSz.setW(java.math.BigInteger.valueOf(11906));
            pgSz.setH(java.math.BigInteger.valueOf(16838));
            // Tight margins: 0.5 inch left/right, 0.3 top, 0.3 bottom
            CTPageMar pgMar = sectPr.getPgMar();
            if (pgMar == null) pgMar = sectPr.addNewPgMar();
            pgMar.setLeft(java.math.BigInteger.valueOf(720));
            pgMar.setRight(java.math.BigInteger.valueOf(720));
            pgMar.setTop(java.math.BigInteger.valueOf(432));
            pgMar.setBottom(java.math.BigInteger.valueOf(432));

            // --- Collect first 2 paragraphs (name + contact) ---
            var elements = doc.getBodyElements();
            String nameText = "";
            String contactText = "";
            XWPFParagraph namePara = null;
            XWPFParagraph contactPara = null;

            // Find name and contact paragraphs (first two XWPFParagraph)
            java.util.List<XWPFParagraph> toRemove = new java.util.ArrayList<>();
            for (int i = 0; i < elements.size() && toRemove.size() < 2; i++) {
                var el = elements.get(i);
                if (el instanceof XWPFParagraph p) {
                    toRemove.add(p);
                    if (toRemove.size() == 1) {
                        nameText = p.getText();
                        namePara = p;
                    } else {
                        contactText = p.getText();
                        contactPara = p;
                    }
                }
            }

            // Remove original name/contact paragraphs by removing runs
            for (XWPFParagraph p : toRemove) {
                for (int r = p.getRuns().size() - 1; r >= 0; r--) {
                    p.removeRun(r);
                }
            }
            // Also remove the empty paragraph elements from body
            // We'll handle this by clearing the paragraphs and re-inserting at table

            // --- Create header table (2 columns, no borders) at position 0 ---
            // We need to insert the table before the first body element
            XWPFTable table;
            XmlCursor cursor = body.getPArray(0).newCursor();
            // Insert table at beginning of body
            CTTbl ctTbl = body.insertNewTbl(cursor);
            cursor.dispose();
            table = new XWPFTable(ctTbl, doc);

            // Remove borders from table
            CTTblPr tblPr = ctTbl.getTblPr();
            if (tblPr == null) tblPr = ctTbl.addNewTblPr();
            CTTblBorders borders = tblPr.addNewTblBorders();
            borders.addNewInsideH().setVal(STBorder.NONE);
            borders.addNewInsideV().setVal(STBorder.NONE);
            borders.addNewLeft().setVal(STBorder.NONE);
            borders.addNewRight().setVal(STBorder.NONE);
            borders.addNewTop().setVal(STBorder.NONE);
            borders.addNewBottom().setVal(STBorder.NONE);

            // Set table width to 100%
            CTTblWidth tblWidth = tblPr.addNewTblW();
            tblWidth.setType(STTblWidth.PCT);
            tblWidth.setW(java.math.BigInteger.valueOf(5000));

            // Create row
            XWPFTableRow row = table.getRow(0);
            // Add second cell (default row has 1 cell)
            row.addNewTableCell();

            XWPFTableCell leftCell = row.getCell(0);
            XWPFTableCell rightCell = row.getCell(1);

            // Set column widths: left 65%, right 35%
            setCellWidth(leftCell, 3250);
            setCellWidth(rightCell, 1750);

            // Remove cell borders
            removeCellBorders(leftCell);
            removeCellBorders(rightCell);

            // --- Left cell: Name + Contact ---
            // Clear default paragraph
            leftCell.removeParagraph(0);

            // Name paragraph
            XWPFParagraph nameP = leftCell.addParagraph();
            nameP.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
            XWPFRun nameRun = nameP.createRun();
            nameRun.setText(nameText.trim());
            nameRun.setFontSize(22);
            nameRun.setBold(true);
            nameRun.setFontFamily("微软雅黑");

            // Contact info paragraph
            XWPFParagraph contactP = leftCell.addParagraph();
            contactP.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT);
            XWPFRun contactRun = contactP.createRun();
            contactRun.setText(contactText.trim());
            contactRun.setFontSize(10);
            contactRun.setFontFamily("微软雅黑");
            contactRun.setColor("444444");

            // Small spacing after contact
            XWPFParagraph spacerP = leftCell.addParagraph();
            XWPFRun spacerRun = spacerP.createRun();
            spacerRun.setFontSize(4);

            // --- Right cell: Photo ---
            // Clear default paragraph
            rightCell.removeParagraph(0);

            XWPFParagraph imgP = rightCell.addParagraph();
            imgP.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
            XWPFRun imgRun = imgP.createRun();

            // Add image - scale to fit ~3cm wide (about 1.2 inches, ~1134 EMU is 1mm, so 30mm = 34020 EMU)
            // For a standard passport photo in resume, about 110 pixels at 96dpi = ~1.1 inch wide
            try (ByteArrayInputStream bis = new ByteArrayInputStream(imgBytes)) {
                imgRun.addPicture(bis,
                        imgType.equals("JPEG") ? XWPFDocument.PICTURE_TYPE_JPEG : XWPFDocument.PICTURE_TYPE_PNG,
                        "photo." + imgType.toLowerCase(),
                        Units.toEMU(1.1),  // width: 1.1 inches
                        Units.toEMU(1.4)); // height: 1.4 inches (aspect ratio ~3:4)
            }

            // --- Now remove the empty paragraphs that used to be name/contact ---
            // They should now be empty; remove them from the body
            for (int i = body.getPList().size() - 1; i >= 0; i--) {
                var p = body.getPList().get(i);
                if (!p.getRList().isEmpty()) continue; // skip non-empty
                boolean isInTable = false;
                for (var tbl : body.getTblList()) {
                    // check if this paragraph is inside a table cell
                }
                // Simple check: if paragraph has no runs, and it's one of the original first two,
                // remove it (they should be empty now)
            }

            // Instead of complex removal, let's make sure empty paragraphs after table don't
            // take up space. We'll reduce font size of empty paragraphs.
            for (int i = 0; i < elements.size(); i++) {
                var el = elements.get(i);
                if (el instanceof XWPFParagraph p) {
                    if (p.getRuns().isEmpty()) {
                        // Empty paragraph - minimize its height
                        XWPFRun r = p.createRun();
                        r.setFontSize(1);
                    }
                }
            }

            // --- Save ---
            String outPath = "C:\\Users\\Administrator\\Desktop\\李志刚_优化版.docx";
            try (FileOutputStream fos = new FileOutputStream(outPath)) {
                doc.write(fos);
            }
            System.out.println("Done! Saved to: " + outPath);
        }
    }

    private static void setCellWidth(XWPFTableCell cell, int widthPercent) {
        CTTc ctTc = cell.getCTTc();
        CTTcPr tcPr = ctTc.getTcPr();
        if (tcPr == null) tcPr = ctTc.addNewTcPr();
        CTTblWidth tcWidth = tcPr.addNewTcW();
        tcWidth.setType(STTblWidth.DXA);
        // A4 usable width ~6 inches = 8640 DXA, allocate proportionally
        tcWidth.setW(java.math.BigInteger.valueOf(widthPercent));
    }

    private static void removeCellBorders(XWPFTableCell cell) {
        CTTc ctTc = cell.getCTTc();
        CTTcPr tcPr = ctTc.getTcPr();
        if (tcPr == null) tcPr = ctTc.addNewTcPr();
        CTTcBorders borders = tcPr.addNewTcBorders();
        borders.addNewLeft().setVal(STBorder.NONE);
        borders.addNewRight().setVal(STBorder.NONE);
        borders.addNewTop().setVal(STBorder.NONE);
        borders.addNewBottom().setVal(STBorder.NONE);
    }
}
