# CLAUDE.md

## 项目概述

文档格式转换器 — Java Swing 桌面应用，支持 7 种文档格式互转（DOCX/DOC/PDF/TXT/HTML/MD/RTF），附带 PDF 合并/拆分工具。

## 构建 & 运行

```bash
export JAVA_HOME="E:/develop/Java/jdk-17"
export PATH="/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.1/plugins/maven/lib/maven3/bin:$PATH"

mvn compile                           # 编译
mvn package -DskipTests               # 打包 → target/FileConverter-1.0.0.jar
java -jar target/FileConverter-1.0.0.jar    # 运行

# 打 Windows 安装包（内嵌 JRE，无需用户安装 Java）
jpackage --type app-image --name "文档格式转换器" --input target \
  --main-jar FileConverter-1.0.0.jar --main-class com.fileconverter.FileConverterApp \
  --dest installer --vendor "FileConverter" --app-version "1.2.0"
```

## 架构

```
com.fileconverter
├── FileConverterApp.java          # 入口，启动 Swing 主窗口
├── converter/
│   ├── Converter.java             # 转换器接口（策略模式）
│   └── DocumentConverter.java     # 文档转换器实现
├── ui/
│   ├── MainWindow.java            # 主窗口：文件列表、拖拽、批量转换、PDF 工具
│   ├── FormatSelector.java        # 格式选择面板：源格式→目标格式映射
│   └── DropPanel.java             # 拖拽区域组件（自定义虚线边框+图标绘制）
└── util/
    ├── FileUtils.java             # 文件工具：扩展名/大小/编码检测
    └── PdfFontUtils.java          # 中文字体单例：加载系统字体嵌入 PDF
```

### 核心设计：两阶段转换模型

`DocumentConverter` 采用「提取 → 写入」两阶段模型：

1. **提取阶段** — 从源文件提取纯文本，各格式有独立提取器
2. **写入阶段** — 将文本写入目标格式

唯一例外是 **DOCX → PDF**，直接走 POI 手工渲染以保留排版（段落、表格、图片、标题层级），不经过文本中间态。

### Converter 接口

```java
interface Converter {
    Set<String> getSourceFormats();
    Set<String> getSupportedConversions(String sourceExtension);
    File convert(File sourceFile, String targetFormat, ProgressCallback callback);
}
```

接口设计支持扩展新转换器（如图片、表格），`FormatSelector` 通过 `CONVERTER_MAP` 自动匹配源格式到对应转换器。

## 依赖要点

| 依赖 | 用途 |
|------|------|
| POI 5.3.0（poi-ooxml + poi-scratchpad） | DOCX 读写（XWPF）+ DOC 读取（HWPF） |
| OpenPDF 2.0.3 | PDF 生成，无 `BaseColor` 类，用 `RGBColor` |
| PDFBox 3.0.3 | PDF 读取 + 合并/拆分，`Loader.loadPDF()` |
| Commonmark 0.21.0 | MD → HTML 解析 |

## 常见坑

- **OpenPDF 类名冲突**：`Document`/`List`/`Row` 与 POI 和 `java.util` 冲突。禁止 `import com.lowagie.text.*`，只导入具体类；冲突时用完全限定名
- **中文编码**：Windows 默认 GBK，TXT/HTML/MD 输入必须用 `FileUtils.readWithEncodingDetection()`
- **中文 PDF 输出**：必须通过 `PdfFontUtils` 嵌入中文字体，默认 Helvetica 无 CJK 字形
- **DOC 限制**：HWPF 只能提取文本，无法提取图片
