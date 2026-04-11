package com.example.virtual.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/**
 * MetadataExtractorService — 多语言代码元数据提取服务
 *
 * 双轨执行策略：
 * - 【NATIVE】优先调用 VNativeEngine 的 C++ 原生实现（线性扫描，~5-8x 性能）
 * - 【JAVA_FALLBACK】若 native 未就绪，降级到 Java Regex 实现（保留原有逻辑）
 *
 * VLang-SH: self-hosting-v1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataExtractorService {

    private final VNativeEngine nativeEngine;

    /**
     * 提取代码的接口/函数符号摘要，为小型 LLM 提供最小上下文。
     * 双轨策略：Native 线性扫描 → Java Regex 降级
     */
    public String extractSummary(String code, String language) {
        if (code == null || code.isBlank()) return "";

        // ── 【NATIVE 路径】──────────────────────────────────
        String nativeResult = nativeEngine.extractSymbols(code, language);
        if (nativeResult != null) {
            log.debug("[MetadataExtractor][NATIVE] Symbols extracted for language: {}", language);
            return nativeResult;
        }

        // ── 【JAVA FALLBACK 路径】──────────────────────────
        log.debug("[MetadataExtractor][JAVA_FALLBACK] Using Java regex for language: {}", language);
        return switch (language.toLowerCase()) {
            case "java"                              -> extractJavaSummary(code);
            case "python"                           -> extractPythonSummary(code);
            case "go"                               -> extractGoSummary(code);
            case "javascript", "js",
                 "typescript", "ts"                 -> extractJsSummary(code);
            case "cpp", "c"                         -> extractCppSummary(code);
            default -> "File content (unsupported for extraction):\n" +
                        (code.length() > 500 ? code.substring(0, 500) + "..." : code);
        };
    }

    // ──────────────────────────────────────────────────────────────
    // 以下 Java Regex 实现保留为降级路径，逻辑与原版完全一致
    // ──────────────────────────────────────────────────────────────

    private String extractJavaSummary(String code) {
        StringBuilder sb = new StringBuilder("Java Interface Summary [Java Fallback]:\n");
        Pattern classPattern = Pattern.compile("(public\\s+(?:class|interface|enum)\\s+\\w+)");
        Matcher cm = classPattern.matcher(code);
        if (cm.find()) sb.append("Structure: ").append(cm.group(1)).append("\n");

        Pattern methodPattern = Pattern.compile("public\\s+([\\w<>\\[\\]\\s]+)\\s+(\\w+)\\s*\\(([^)]*)\\)");
        Matcher mm = methodPattern.matcher(code);
        while (mm.find()) {
            sb.append("- Method: ").append(mm.group(2))
              .append("(").append(mm.group(3).trim()).append(")")
              .append(" -> ").append(mm.group(1).trim()).append("\n");
        }
        return sb.toString();
    }

    private String extractPythonSummary(String code) {
        StringBuilder sb = new StringBuilder("Python Interface Summary [Java Fallback]:\n");
        Pattern funcPattern = Pattern.compile("def\\s+(\\w+)\\s*\\(([^)]*)\\)(?:\\s*->\\s*([\\w\\[\\],\\s]+))?:");
        Matcher fm = funcPattern.matcher(code);
        while (fm.find()) {
            sb.append("- Func: ").append(fm.group(1))
              .append("(").append(fm.group(2).trim()).append(")");
            if (fm.group(3) != null) sb.append(" -> ").append(fm.group(3).trim());
            sb.append("\n");
        }
        return sb.toString();
    }

    private String extractGoSummary(String code) {
        StringBuilder sb = new StringBuilder("Go Interface Summary [Java Fallback]:\n");
        Pattern funcPattern = Pattern.compile("func\\s+(?:\\([^)]+\\)\\s+)?(\\w+)\\s*\\(([^)]*)\\)(?:\\s*\\(?([^){]*)?\\)?)?\\s*\\{");
        Matcher fm = funcPattern.matcher(code);
        while (fm.find()) {
            String name = fm.group(1);
            if (Character.isUpperCase(name.charAt(0))) {
                sb.append("- Exported: ").append(name)
                  .append("(").append(fm.group(2).trim()).append(")");
                if (fm.group(3) != null && !fm.group(3).isBlank()) sb.append(" -> ").append(fm.group(3).trim());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String extractJsSummary(String code) {
        StringBuilder sb = new StringBuilder("JS/TS Interface Summary [Java Fallback]:\n");
        Pattern funcPattern = Pattern.compile("export\\s+(?:async\\s+)?(?:function\\s+(\\w+)|const\\s+(\\w+)\\s*=\\s*\\([^)]*\\)\\s*=>)");
        Matcher fm = funcPattern.matcher(code);
        while (fm.find()) {
            String name = fm.group(1) != null ? fm.group(1) : fm.group(2);
            sb.append("- Exported: ").append(name).append("\n");
        }
        return sb.toString();
    }

    private String extractCppSummary(String code) {
        StringBuilder sb = new StringBuilder("C/C++ Interface Summary [Java Fallback]:\n");
        Pattern funcPattern = Pattern.compile("([\\w:*&<>]+)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:const)?\\s*\\{");
        Matcher fm = funcPattern.matcher(code);
        while (fm.find()) {
            sb.append("- Symbol: ").append(fm.group(2))
              .append("(").append(fm.group(3).trim()).append(")")
              .append(" -> ").append(fm.group(1).trim()).append("\n");
        }
        return sb.toString();
    }
}
