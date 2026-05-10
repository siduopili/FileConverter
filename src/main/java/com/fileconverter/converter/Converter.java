package com.fileconverter.converter;

import java.io.File;
import java.util.Set;

public interface Converter {
    /** 输入格式 -> 可转换的输出格式集合 */
    Set<String> getSupportedConversions(String sourceExtension);

    /** 执行转换，返回输出文件 */
    File convert(File sourceFile, String targetFormat, ProgressCallback callback) throws Exception;

    /** 该转换器负责的源格式 */
    Set<String> getSourceFormats();

    interface ProgressCallback {
        void onProgress(int percent, String message);
    }
}
