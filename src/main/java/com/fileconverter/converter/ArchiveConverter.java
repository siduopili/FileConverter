package com.fileconverter.converter;

import com.fileconverter.util.FileUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class ArchiveConverter implements Converter {

    private static final Set<String> SOURCE_FORMATS = Set.of("zip", "tar", "tar.gz", "tgz", "gz");

    @Override public Set<String> getSourceFormats() { return SOURCE_FORMATS; }

    @Override
    public Set<String> getSupportedConversions(String sourceExtension) {
        var targets = new HashSet<>(Set.of("zip", "tar", "tar.gz"));
        targets.remove(sourceExtension.toLowerCase());
        return targets;
    }

    @Override
    public File convert(File sourceFile, String targetFormat, ProgressCallback callback) throws Exception {
        String srcExt = FileUtils.getExtension(sourceFile);
        String tgtExt = targetFormat.toLowerCase();
        String baseName = FileUtils.stripExtension(sourceFile.getName());

        callback.onProgress(10, "解压源文件...");
        Path tempDir = Files.createTempDirectory("fc_archive_");
        try {
            extractArchive(sourceFile, srcExt, tempDir);
            callback.onProgress(50, "创建目标压缩包...");
            File outputFile = new File(sourceFile.getParent(), baseName + "." + tgtExt);
            createArchive(tempDir, outputFile, tgtExt);
            callback.onProgress(100, "完成");
            return outputFile;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void extractArchive(File sourceFile, String ext, Path targetDir) throws Exception {
        Files.createDirectories(targetDir);
        if ("zip".equals(ext)) {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path outPath = targetDir.resolve(sanitizeName(entry.getName()));
                    if (entry.isDirectory()) Files.createDirectories(outPath);
                    else { Files.createDirectories(outPath.getParent()); Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        } else if ("tar".equals(ext)) {
            try (org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis =
                         new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(new FileInputStream(sourceFile))) {
                org.apache.commons.compress.archivers.ArchiveEntry entry;
                while ((entry = tis.getNextEntry()) != null) {
                    Path outPath = targetDir.resolve(sanitizeName(entry.getName()));
                    if (entry.isDirectory()) Files.createDirectories(outPath);
                    else { Files.createDirectories(outPath.getParent()); Files.copy(tis, outPath, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        } else if ("tar.gz".equals(ext) || "tgz".equals(ext)) {
            try (org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis =
                         new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                                 new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(new FileInputStream(sourceFile)))) {
                org.apache.commons.compress.archivers.ArchiveEntry entry;
                while ((entry = tis.getNextEntry()) != null) {
                    Path outPath = targetDir.resolve(sanitizeName(entry.getName()));
                    if (entry.isDirectory()) Files.createDirectories(outPath);
                    else { Files.createDirectories(outPath.getParent()); Files.copy(tis, outPath, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        } else if ("gz".equals(ext)) {
            String name = FileUtils.stripExtension(sourceFile.getName());
            try (org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream gis =
                         new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(new FileInputStream(sourceFile))) {
                Files.copy(gis, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void createArchive(Path sourceDir, File outputFile, String ext) throws Exception {
        List<Path> files = Files.walk(sourceDir).filter(p -> !p.equals(sourceDir)).toList();
        if ("zip".equals(ext)) {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                for (Path path : files) {
                    String name = sourceDir.relativize(path).toString().replace('\\', '/');
                    if (Files.isDirectory(path)) {
                        zos.putNextEntry(new ZipEntry(name.endsWith("/") ? name : name + "/"));
                        zos.closeEntry();
                    } else {
                        zos.putNextEntry(new ZipEntry(name));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                }
            }
        } else if ("tar".equals(ext)) {
            try (org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tos =
                         new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(new FileOutputStream(outputFile))) {
                for (Path path : files) {
                    String name = sourceDir.relativize(path).toString().replace('\\', '/');
                    tos.putArchiveEntry(new org.apache.commons.compress.archivers.tar.TarArchiveEntry(path.toFile(), name));
                    if (!Files.isDirectory(path)) Files.copy(path, tos);
                    tos.closeArchiveEntry();
                }
                tos.finish();
            }
        } else if ("tar.gz".equals(ext)) {
            try (org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tos =
                         new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(
                                 new org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream(new FileOutputStream(outputFile)))) {
                for (Path path : files) {
                    String name = sourceDir.relativize(path).toString().replace('\\', '/');
                    tos.putArchiveEntry(new org.apache.commons.compress.archivers.tar.TarArchiveEntry(path.toFile(), name));
                    if (!Files.isDirectory(path)) Files.copy(path, tos);
                    tos.closeArchiveEntry();
                }
                tos.finish();
            }
        }
    }

    private String sanitizeName(String name) {
        // Basic path traversal protection
        name = name.replace("\0", "");
        while (name.startsWith("/") || name.startsWith("\\")) name = name.substring(1);
        if (name.contains("..")) name = name.replace("..", "_");
        return name;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }
}
