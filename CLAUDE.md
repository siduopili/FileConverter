# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Must use JDK 17 and IntelliJ's bundled Maven (not system-installed)
export JAVA_HOME="E:/develop/Java/jdk-17"
export PATH="/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.1/plugins/maven/lib/maven3/bin:$PATH"

mvn compile                           # Compile
mvn package -DskipTests               # Build fat JAR → target/FileConverter-1.0.0.jar
java -jar target/FileConverter-1.0.0.jar    # Run

# Build standalone Windows package (bundled JRE, no Java required for end users)
rm -rf installer
jpackage --type app-image --name "万能格式转换器" --input target \
  --main-jar FileConverter-1.0.0.jar --main-class com.fileconverter.FileConverterApp \
  --dest installer --vendor "FileConverter" --app-version "1.2.0"
```

## Architecture

All converters implement `Converter` (`getSourceFormats` / `getSupportedConversions` / `convert` + `ProgressCallback`). The UI discovers converters by iterating a hardcoded list in `FormatSelector`.

**Converters** (`converter/`):

- `DocumentConverter` — DOCX/DOC/PDF/TXT/HTML/MD/RTF. DOCX→PDF has a **three-tier fallback**: Word COM (PowerShell `ExportAsFixedFormat`, perfect layout) → LibreOffice headless → POI manual rendering. DOC uses text-only path since HWPF can't extract images.
- `ImageConverter` — PNG/JPG/BMP/GIF/WebP/ICO/TIFF. TwelveMonkeys handles WebP/TIFF reading. WebP **writing is unsupported** (TwelveMonkeys provides no writer); code detects this and throws descriptive error.
- `SpreadsheetConverter` — XLSX/XLS/CSV/JSON ↔ PDF. XLSX→PDF renders directly from `Workbook` object (not text extraction) to preserve borders and merged cells (`CellRangeAddress`).
- `PptxConverter` — PPTX→PDF/PNG/JPG/TXT. Renders each slide to `BufferedImage` via `XSLFSlide.draw()`, embeds into PDF pages.
- `ArchiveConverter` — ZIP/TAR/TAR.GZ. Extracts to temp dir, recompresses to target format. Uses `java.util.zip` for ZIP, Apache Commons Compress for TAR/GZ.
- `MediaConverter` — Audio/video via external `ffmpeg` (detected from PATH or common install dirs). Not bundled.

**Utilities** (`util/`):

- `FileUtils` — `getExtension()` handles `.tar.gz`; `stripExtension()`; `formatSize()`; `readWithEncodingDetection()` tries UTF-8 then GBK.
- `PdfFontUtils` — Thread-safe singleton for Chinese PDF font. Loads from `C:\Windows\Fonts\msyh.ttc` → `simsun.ttc` → `simhei.ttf` with macOS/Linux fallbacks. Provides `getNormalFont()`, `getBoldFont()`, `createFont(size)`.

**UI** (`ui/`, Swing):

- `MainWindow` — File list (multi-select + drag-drop), batch conversion with cross-format validation, cancel button, PDF merge/split tools.
- `FormatSelector` — Maps source file extension to converter, populates target format dropdown.
- `DropPanel` — Custom-painted drag-drop zone with dashed border and upload icon.

## Dependency Notes

| Library | Key detail |
|---------|------------|
| OpenPDF 2.0.3 | No `BaseColor` class — use `RGBColor` instead |
| PDFBox 3.0.3 | `Loader.loadPDF()`, not `PDDocument.load()` |
| POI 5.3.0 | `poi-scratchpad` needed for HWPF (old .doc) |
| TwelveMonkeys | Read-only for WebP; no encoder included |

## Common Pitfalls

- **Import conflicts**: OpenPDF classes (`Document`, `List`, `Row`) collide with POI and `java.util`. Never use `com.lowagie.text.*` — import only specific classes. Use `com.lowagie.text.Document` fully-qualified when POI's `XWPFDocument` is also in scope.
- **Chinese encoding**: Windows defaults to GBK. Always use `FileUtils.readWithEncodingDetection()` for TXT/HTML/MD/CSV/JSON input files instead of `Files.readString()`.
- **Chinese PDF output**: Must use `PdfFontUtils` (embeds a Chinese TTF font). Default Helvetica has zero CJK glyphs — all Chinese will be blank squares.
- **Word COM**: Only works with Microsoft Office installed (not WPS). Uses `wdExportFormatPDF=17`. If COM fails, the converter silently falls back to LibreOffice or POI.
