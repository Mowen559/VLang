package com.example.virtual.service;

import com.example.virtual.domain.VirtualObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * VNativeBootstrapSeeder — 意图自托管种子服务
 *
 * 这是 VLang 自举中最"哲学"的一环：
 * 将 C++ 编译器源码本身作为一个 VirtualObject 存储在 VLang IDE 中，
 * 实现"编译器由它编译出的语言所管理"的意图自托管闭环。
 *
 * 初始化流程：
 * 1. 检查是否已存在名为 "__vlang_native_parser_src__" 的系统 VirtualObject
 * 2. 若不存在，从 classpath 读取 C++ 源码并创建该对象
 * 3. 该对象在 VLang IDE 中可见、可编辑，对其的修改可触发重新编译
 *
 * VLang-SH: self-hosting-v1.0 — Intent Self-Hosting
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VNativeBootstrapSeeder {

    /** 系统保留名称，用于标识自举 VirtualObject */
    public static final String NATIVE_PARSER_OBJ_NAME = "__vlang_native_parser_src__";
    public static final String NATIVE_PARSER_DISPLAY  = "🔧 VLang 自举编译器核心 (vnative_parser.cpp)";

    private final VirtualObjectService virtualObjectService;

    /**
     * Spring 启动后执行。使用 @PostConstruct 确保 Repository 已初始化。
     * 这是幂等的：多次运行不会创建重复对象。
     */
    @PostConstruct
    public void seedNativeParserObject() {
        try {
            // 检查是否已存在（幂等保证）
            List<VirtualObject> existing = virtualObjectService.findByName(NATIVE_PARSER_OBJ_NAME);
            if (!existing.isEmpty()) {
                log.info("[VLang Self-Hosting] Native parser VirtualObject already exists (id={}), skip seeding.",
                        existing.get(0).getId());
                return;
            }

            // 从 classpath 读取 C++ 源码
            String cppSource = readClasspathResource("native/vnative_parser.cpp");
            String hSource   = readClasspathResource("native/vnative_parser.h");

            // 构建 VLang FrontMatter（意图描述头）
            String frontMatter = """
                    ---
                    name: %s
                    language: cpp
                    type: system
                    description: |
                      VLang 自举编译器核心 C++ 实现。
                      本文件由 VLang 意图系统托管，修改后可触发编译器热更新。
                      包含三个核心能力：FrontMatter FSM解析器、多语言符号提取器、JNI桥接层。
                    VLang-SH: self-hosting-v1.0
                    bootstrap: true
                    ---
                    """.formatted(NATIVE_PARSER_OBJ_NAME);

            // 意图内容 = FrontMatter + C++ 源码
            String fullContent = frontMatter + "\n" + cppSource;

            // 创建 VirtualObject
            VirtualObject parserObj = new VirtualObject();
            parserObj.setName(NATIVE_PARSER_OBJ_NAME);
            parserObj.setType(VirtualObject.ObjectType.FILE);
            parserObj.setLanguage("cpp");
            parserObj.setContent(fullContent);
            parserObj.setGeneratedCode(cppSource); // 生成代码就是 C++ 本身
            parserObj.setContentHash(sha256(fullContent));
            parserObj.setCompiledHash(sha256(fullContent)); // 初始视为已编译
            parserObj.setBuildStatus(VirtualObject.BuildStatus.RELIABLE);
            parserObj.setCreateTime(LocalDateTime.now());
            parserObj.setUpdateTime(LocalDateTime.now());

            VirtualObject saved = virtualObjectService.create(parserObj);

            log.info("[VLang Self-Hosting] ✓ Seeded native parser VirtualObject: id={}, name='{}'",
                    saved.getId(), NATIVE_PARSER_DISPLAY);
            log.info("[VLang Self-Hosting] ✓ The C++ compiler source is now managed by VLang IDE.");
            log.info("[VLang Self-Hosting] ✓ Intent Self-Hosting loop is CLOSED.");

            // 同样为头文件创建一个配套 VirtualObject
            seedHeaderObject(hSource, saved.getId());

        } catch (Exception e) {
            // 种子失败不阻塞系统运行
            log.warn("[VLang Self-Hosting] Failed to seed native parser object (non-critical): {}", e.getMessage());
        }
    }

    /**
     * 创建头文件配套对象（作为 .cpp 的子对象）
     */
    private void seedHeaderObject(String hSource, Long parentId) {
        try {
            String headerName = "__vlang_native_parser_h__";
            List<VirtualObject> existing = virtualObjectService.findByName(headerName);
            if (!existing.isEmpty()) return;

            VirtualObject hObj = new VirtualObject();
            hObj.setName(headerName);
            hObj.setType(VirtualObject.ObjectType.FILE);
            hObj.setLanguage("cpp");
            hObj.setParentId(parentId);
            hObj.setContent(hSource);
            hObj.setGeneratedCode(hSource);
            hObj.setContentHash(sha256(hSource));
            hObj.setCompiledHash(sha256(hSource));
            hObj.setBuildStatus(VirtualObject.BuildStatus.RELIABLE);
            hObj.setCreateTime(LocalDateTime.now());
            hObj.setUpdateTime(LocalDateTime.now());
            virtualObjectService.create(hObj);

            log.info("[VLang Self-Hosting] ✓ Seeded native header VirtualObject.");
        } catch (Exception e) {
            log.warn("[VLang Self-Hosting] Failed to seed header object: {}", e.getMessage());
        }
    }

    /**
     * 读取 classpath 资源为字符串
     */
    private String readClasspathResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * SHA-256 哈希（与 VirtualObjectService 保持一致）
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
