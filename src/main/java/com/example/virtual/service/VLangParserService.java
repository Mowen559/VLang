package com.example.virtual.service;

import com.example.virtual.domain.VirtualObject;
import com.example.virtual.repository.VirtualObjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VLangParserService — VLang FrontMatter 解析服务
 *
 * 双轨执行策略：
 * - 【NATIVE】优先调用 VNativeEngine 的 C++ 原生实现（FSM，~8-12x 性能）
 * - 【JAVA_FALLBACK】若 native 未就绪，降级到 Java Regex 实现（零破坏性兼容）
 *
 * VLang-SH: self-hosting-v1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VLangParserService {

    private final VirtualObjectRepository repository;
    private final VNativeEngine nativeEngine;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final Pattern FRONT_MATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);

    /**
     * 解析 VLang 内容，提取依赖并解析为对象 ID 列表。
     * 双轨策略：Native JSON 解析 → Java YAML 解析降级
     */
    public List<Long> parseAndResolveDependencies(String content) {
        List<Long> resolvedIds = new ArrayList<>();
        if (content == null || content.isBlank()) return resolvedIds;

        // ── 【NATIVE 路径】──────────────────────────────────
        String nativeJson = nativeEngine.parseFrontMatterJson(content);
        if (nativeJson != null) {
            return resolveFromJson(nativeJson);
        }

        // ── 【JAVA FALLBACK 路径】──────────────────────────
        log.debug("[VLangParser] Using Java fallback for dependency parsing.");
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            String yamlContent = matcher.group(1);
            try {
                Map<String, Object> metadata = yamlMapper.readValue(yamlContent, new TypeReference<>() {});
                Object importsObj = metadata.get("imports");

                if (importsObj instanceof List<?> importsList) {
                    for (Object imp : importsList) {
                        if (imp instanceof Number) {
                            resolvedIds.add(((Number) imp).longValue());
                        } else if (imp instanceof String name) {
                            resolvedIds.addAll(resolveNameToIds(name));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[VLangParser][JAVA_FALLBACK] Failed to parse FrontMatter: {}", e.getMessage());
            }
        }
        return resolvedIds;
    }

    /**
     * 从 C++ native 返回的 JSON 中解析 imports 列表
     * JSON 格式: {"imports":[42, "some-name", ...]}
     */
    private List<Long> resolveFromJson(String json) {
        List<Long> ids = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(json, new TypeReference<>() {});
            Object importsObj = parsed.get("imports");
            if (importsObj instanceof List<?> importsList) {
                for (Object imp : importsList) {
                    if (imp instanceof Number) {
                        ids.add(((Number) imp).longValue());
                    } else if (imp instanceof String name) {
                        ids.addAll(resolveNameToIds(name));
                    }
                }
            }
            log.debug("[VLangParser][NATIVE] Resolved {} dependencies.", ids.size());
        } catch (Exception e) {
            log.warn("[VLangParser][NATIVE] Failed to parse native JSON, falling back: {}", e.getMessage());
            return null; // 触发上层重试 Java 路径
        }
        return ids;
    }

    private List<Long> resolveNameToIds(String name) {
        List<Long> ids = new ArrayList<>();
        List<VirtualObject> matches = repository.findByName(name);
        if (!matches.isEmpty()) {
            ids.add(matches.get(0).getId());
            log.info("[VLangParser] Resolved name '{}' to ID {}", name, matches.get(0).getId());
        } else {
            log.warn("[VLangParser] Could not resolve VLang import name: {}", name);
        }
        return ids;
    }

    /**
     * 提取 VLang 意图正文（剥离 FrontMatter）。
     * 双轨策略：Native FSM → Java Regex 降级
     */
    public String extractIntentBody(String content) {
        if (content == null) return "";

        // ── 【NATIVE 路径】──────────────────────────────────
        String nativeResult = nativeEngine.extractIntentBody(content);
        if (nativeResult != null) {
            log.debug("[VLangParser][NATIVE FrontMatter Parser] Intent body extracted.");
            return nativeResult;
        }

        // ── 【JAVA FALLBACK 路径】──────────────────────────
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            return content.substring(matcher.end()).trim();
        }
        return content.trim();
    }
}
