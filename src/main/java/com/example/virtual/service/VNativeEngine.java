package com.example.virtual.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * VNativeEngine — VLang 自举核心 JNI 桥接服务
 *
 * 职责：
 * 1. 在 Spring 启动后异步触发"自举引导"（B策略：@PostConstruct 异步预编译）
 * 2. 从 classpath 内嵌资源提取 C++ 源码
 * 3. 调用 MinGW g++ 编译为 vnative_parser.dll
 * 4. 通过 System.load() 加载动态库，启用 native 方法
 * 5. 提供 extractIntentBody / extractSymbols / parseFrontMatterJson 三个带降级的包装方法
 *
 * 自举路径：
 *   [classpath:native/vnative_parser.cpp]
 *       → g++ -shared → vnative_parser.dll
 *       → System.load() → native methods ready
 *       → VLangParserService & MetadataExtractorService 调用 native 引擎
 *
 * VLang-SH: self-hosting-v1.0
 */
@Slf4j
@Service
public class VNativeEngine {

    /** 编译器路径（与 ExecutionService 保持一致）*/
    private static final String GPP_PATH = "F:\\2\\mingw\\mingw64\\bin\\g++.exe";

    /** native 方法状态标记 */
    @Getter
    private final AtomicBoolean nativeReady = new AtomicBoolean(false);

    /** 编译后 dll 的绝对路径（供日志与状态端点使用）*/
    @Getter
    private volatile String nativeLibPath = null;

    /** 自举错误信息（若失败） */
    @Getter
    private volatile String bootstrapError = null;

    // ──────────────────────────────────────────────────
    // JNI Native 方法声明（由 C++ 实现）
    // ──────────────────────────────────────────────────

    /** 提取 VLang 意图正文（剥离 FrontMatter）*/
    public native String nativeExtractIntentBody(String content);

    /** 解析 FrontMatter，返回 JSON 格式的 imports 列表 */
    public native String nativeParseFrontMatterJson(String content);

    /** 提取多语言符号摘要 */
    public native String nativeExtractSymbols(String code, String language);

    /** 版本检查（引导验证） */
    public native String nativeVersion();

    // ──────────────────────────────────────────────────
    // 工业化引导：B策略 @PostConstruct 异步预编译
    // ──────────────────────────────────────────────────

    /**
     * Spring 完成 Bean 初始化后立即触发，异步执行，不阻塞启动。
     * 这是工业标准做法（类似 JVM JIT 热身、Webpack HMR 预编译）。
     */
    @PostConstruct
    public void bootstrapAsync() {
        log.info("[VLang Bootstrap] Initiating async self-hosting bootstrap...");
        CompletableFuture.runAsync(() -> {
            try {
                doBootstrap();
                log.info("[VLang Bootstrap] ✓ Native engine loaded: {}", nativeLibPath);
                // 验证 JNI 链接
                String version = nativeVersion();
                log.info("[VLang Bootstrap] ✓ Version check: {}", version);
            } catch (Exception e) {
                bootstrapError = e.getMessage();
                log.warn("[VLang Bootstrap] ✗ Native engine bootstrap failed (Java fallback active): {}", e.getMessage());
            }
        });
    }

    /**
     * 自举核心引导流程：
     * Step 1: 从 classpath 提取 C++ 源码到临时目录
     * Step 2: 调用 g++ 编译为 .dll
     * Step 3: System.load() 加载，注册 native 方法
     */
    private void doBootstrap() throws Exception {
        // Step 1: 提取 C++ 源文件到临时目录
        Path tempDir = Files.createTempDirectory("vlang_native_bootstrap_");
        Path cppFile = tempDir.resolve("vnative_parser.cpp");
        Path hFile   = tempDir.resolve("vnative_parser.h");
        Path dllFile = tempDir.resolve("vnative_parser.dll");

        extractResource("native/vnative_parser.cpp", cppFile);
        extractResource("native/vnative_parser.h",   hFile);
        log.info("[VLang Bootstrap] C++ sources extracted to: {}", tempDir);

        // Step 2: 检测 JAVA_HOME，用于 JNI 头文件路径
        String javaHome = System.getProperty("java.home");
        if (javaHome.endsWith("jre")) {
            javaHome = javaHome.substring(0, javaHome.length() - 4);
        }
        String includeDir     = javaHome + "\\include";
        String includePlatDir = javaHome + "\\include\\win32";

        // Step 3: 调用 g++ 编译
        log.info("[VLang Bootstrap] Compiling C++ native module with g++...");
        ProcessBuilder pb = new ProcessBuilder(
            GPP_PATH,
            "-shared",
            "-o", dllFile.toString(),
            cppFile.toString(),
            "-I", tempDir.toString(),       // 使包含 vnative_parser.h 可被找到
            "-I", includeDir,
            "-I", includePlatDir,
            "-O2",
            "-std=c++17",
            "-static-libgcc",
            "-static-libstdc++",
            "-Wl,--kill-at"
        );
        pb.redirectErrorStream(true);
        pb.directory(tempDir.toFile());

        Process proc = pb.start();
        String compileOutput = new BufferedReader(new InputStreamReader(proc.getInputStream()))
                .lines().collect(Collectors.joining("\n"));
        int exitCode = proc.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("g++ compilation failed (exit=" + exitCode + "):\n" + compileOutput);
        }
        log.info("[VLang Bootstrap] g++ compilation successful.");

        // Step 4: 加载动态库
        nativeLibPath = dllFile.toAbsolutePath().toString();
        System.load(nativeLibPath);
        nativeReady.set(true);
    }

    /**
     * 从 classpath 资源提取到目标路径
     */
    private void extractResource(String resourcePath, Path target) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found in classpath: " + resourcePath);
            }
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ──────────────────────────────────────────────────
    // 公开包装方法：带降级的 Native/Java 双轨调用
    // ──────────────────────────────────────────────────

    /**
     * 提取意图正文（优先 Native，降级 Java）
     * @return null 表示 native 不可用，调用方执行 Java fallback
     */
    public String extractIntentBody(String content) {
        if (!nativeReady.get()) return null;
        try {
            return nativeExtractIntentBody(content);
        } catch (UnsatisfiedLinkError e) {
            nativeReady.set(false);
            log.warn("[VNativeEngine] JNI link error on extractIntentBody, disabling native: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 FrontMatter imports 为 JSON（优先 Native，降级 Java）
     * @return null 表示 native 不可用
     */
    public String parseFrontMatterJson(String content) {
        if (!nativeReady.get()) return null;
        try {
            return nativeParseFrontMatterJson(content);
        } catch (UnsatisfiedLinkError e) {
            nativeReady.set(false);
            log.warn("[VNativeEngine] JNI link error on parseFrontMatterJson, disabling native: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取符号摘要（优先 Native，降级 Java）
     * @return null 表示 native 不可用
     */
    public String extractSymbols(String code, String language) {
        if (!nativeReady.get()) return null;
        try {
            return nativeExtractSymbols(code, language);
        } catch (UnsatisfiedLinkError e) {
            nativeReady.set(false);
            log.warn("[VNativeEngine] JNI link error on extractSymbols, disabling native: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取引擎状态描述（用于 /bootstrap/status 端点）
     */
    public String getStatusReport() {
        if (nativeReady.get()) {
            return String.format(
                "{\"status\":\"NATIVE_READY\",\"engine\":\"VLang-Native-v1.0\",\"libPath\":\"%s\",\"selfHosting\":true}",
                nativeLibPath != null ? nativeLibPath.replace("\\", "\\\\") : "unknown"
            );
        } else if (bootstrapError != null) {
            return String.format(
                "{\"status\":\"JAVA_FALLBACK\",\"reason\":\"%s\",\"selfHosting\":false}",
                bootstrapError.replace("\"", "'")
            );
        } else {
            return "{\"status\":\"BOOTSTRAPPING\",\"selfHosting\":false}";
        }
    }
}
