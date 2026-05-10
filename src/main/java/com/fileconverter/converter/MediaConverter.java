package com.fileconverter.converter;

import com.fileconverter.util.FileUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MediaConverter implements Converter {

    private static final Set<String> SOURCE_FORMATS = Set.of(
            "mp3", "wav", "ogg", "flac", "aac", "m4a", "wma",
            "mp4", "avi", "mov", "mkv", "webm", "flv", "wmv", "gif"
    );
    private static final Set<String> AUDIO = Set.of("mp3", "wav", "ogg", "flac", "aac", "m4a", "wma");
    private static final Set<String> VIDEO = Set.of("mp4", "avi", "mov", "mkv", "webm", "flv", "wmv");

    @Override public Set<String> getSourceFormats() { return SOURCE_FORMATS; }

    @Override
    public Set<String> getSupportedConversions(String sourceExtension) {
        String src = sourceExtension.toLowerCase();
        var targets = new HashSet<String>();
        if (AUDIO.contains(src)) targets.addAll(AUDIO);
        if (VIDEO.contains(src)) { targets.addAll(VIDEO); targets.add("gif"); targets.addAll(AUDIO); }
        if ("gif".equals(src)) targets.addAll(Set.of("mp4", "webm", "avi"));
        targets.remove(src);
        return targets;
    }

    @Override
    public File convert(File sourceFile, String targetFormat, ProgressCallback callback) throws Exception {
        String tgtExt = targetFormat.toLowerCase();
        String baseName = FileUtils.stripExtension(sourceFile.getName());

        String ffmpeg = findFfmpeg();
        if (ffmpeg == null) {
            throw new IOException("未找到 ffmpeg。请安装 ffmpeg 并添加到 PATH。\n下载: https://ffmpeg.org/download.html");
        }

        callback.onProgress(10, "调用 ffmpeg 转换...");
        File outputFile = new File(sourceFile.getParent(), baseName + "." + tgtExt);

        List<String> cmd = buildCommand(ffmpeg, sourceFile, outputFile, FileUtils.getExtension(sourceFile), tgtExt);
        callback.onProgress(30, "转码中...");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("time=")) callback.onProgress(50, line.trim());
            }
        }

        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("转换超时（10分钟）");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg 转换失败，退出码: " + process.exitValue());
        }
        callback.onProgress(100, "完成");
        return outputFile;
    }

    private String findFfmpeg() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) return "ffmpeg";
        } catch (Exception ignored) {}
        String[] paths = {
            System.getenv("USERPROFILE") + "\\ffmpeg\\bin\\ffmpeg.exe",
            "C:\\ffmpeg\\bin\\ffmpeg.exe",
            "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe"
        };
        for (String p : paths) if (new File(p).exists()) return p;
        return null;
    }

    private List<String> buildCommand(String ffmpeg, File input, File output, String srcExt, String tgtExt) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg); cmd.add("-y"); cmd.add("-i"); cmd.add(input.getAbsolutePath());

        if (AUDIO.contains(srcExt) && AUDIO.contains(tgtExt)) {
            cmd.add("-q:a"); cmd.add("2");
        } else if (VIDEO.contains(srcExt) && "gif".equals(tgtExt)) {
            cmd.add("-vf"); cmd.add("fps=10,scale=480:-1:flags=lanczos");
        } else if ("gif".equals(srcExt) && VIDEO.contains(tgtExt)) {
            cmd.add("-c:v"); cmd.add("libx264"); cmd.add("-pix_fmt"); cmd.add("yuv420p");
        } else if (VIDEO.contains(srcExt) && AUDIO.contains(tgtExt)) {
            cmd.add("-vn"); cmd.add("-q:a"); cmd.add("2");
        } else {
            cmd.add("-c:v"); cmd.add("libx264"); cmd.add("-preset"); cmd.add("fast");
            cmd.add("-crf"); cmd.add("23");
            if (AUDIO.contains(tgtExt)) cmd.add("-vn");
        }
        cmd.add(output.getAbsolutePath());
        return cmd;
    }
}
