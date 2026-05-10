package com.fileconverter;

import org.apache.poi.xwpf.usermodel.*;
import java.io.*;
import java.util.*;

public class DocxInspector {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "C:\\Users\\Administrator\\Desktop\\李志刚_优化版.docx";
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path))) {
            System.out.println("=== Paragraphs ===");
            for (int i = 0; i < doc.getParagraphs().size(); i++) {
                XWPFParagraph p = doc.getParagraphs().get(i);
                String text = p.getText();
                String style = p.getStyle();
                String alignment = p.getAlignment() != null ? p.getAlignment().name() : "null";
                System.out.println("P" + i + " style=" + style + " align=" + alignment + " | " + (text.length() > 100 ? text.substring(0, 100) + "..." : text));
            }
            System.out.println("\n=== Body elements ===");
            for (int i = 0; i < doc.getBodyElements().size(); i++) {
                var el = doc.getBodyElements().get(i);
                System.out.println("E" + i + " type=" + el.getClass().getSimpleName());
                if (el instanceof XWPFTable t) {
                    System.out.println("  Table rows=" + t.getRows().size());
                    for (int j = 0; j < t.getRows().size(); j++) {
                        var row = t.getRow(j);
                        System.out.print("  Row" + j + ": ");
                        for (int k = 0; k < row.getTableCells().size(); k++) {
                            System.out.print("[" + row.getCell(k).getText().replace("\n", "|") + "] ");
                        }
                        System.out.println();
                    }
                }
            }
            // Page/margin info
            var sectPr = doc.getDocument().getBody().getSectPr();
            if (sectPr != null) {
                var pgSz = sectPr.getPgSz();
                if (pgSz != null) System.out.println("\nPage size: " + pgSz.getW() + " x " + pgSz.getH() + " (EMU)");
                var pgMar = sectPr.getPgMar();
                if (pgMar != null) System.out.println("Margins L:" + pgMar.getLeft() + " R:" + pgMar.getRight() + " T:" + pgMar.getTop() + " B:" + pgMar.getBottom());
            }
        }
    }
}
