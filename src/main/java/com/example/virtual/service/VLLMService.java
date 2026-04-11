package com.example.virtual.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VLLMService — VLang 智能代码生成服务
 *
 * 双轨调用策略：
 *  - 主路径：Intelligence 内部服务（http://localhost:8010/chat/sync）
 *  - 降级路径：OpenAI 兼容 API（DeepSeek / 硅基流动 等）
 *
 * 配置示例 (application.properties):
 *   intelligence.api.url=http://localhost:8010/chat/sync
 *   vlang.fallback.api.url=https://api.deepseek.com/v1/chat/completions
 *   vlang.fallback.api.key=sk-xxxxxxxxxxxxxxxx
 *   vlang.fallback.api.model=deepseek-coder
 */
@Service
public class VLLMService {

    private final HttpClient httpClient;

    // 主路径：Intelligence 内部服务
    @Value("${intelligence.api.url:http://localhost:8010/chat/sync}")
    private String intelligenceApiUrl;

    // 降级路径：OpenAI 兼容 API
    @Value("${vlang.fallback.api.url:https://api.siliconflow.cn/v1/chat/completions}")
    private String fallbackApiUrl;

    @Value("${vlang.fallback.api.key:}")
    private String fallbackApiKey;

    @Value("${vlang.fallback.api.model:Qwen/Qwen2.5-Coder-7B-Instruct}")
    private String fallbackModel;

    public VLLMService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 生成代码：先尝试 Intelligence 主服务，502/失败时自动降级到外部 API
     */
    public String generateCode(String prompt, String targetLanguage) {
        // ── 主路径：Intelligence 服务 ──────────────────────
        String intelligenceResult = callIntelligence(prompt, targetLanguage);
        if (intelligenceResult != null) {
            return intelligenceResult;
        }

        // ── 降级路径：OpenAI 兼容 API ─────────────────────
        if (fallbackApiKey != null && !fallbackApiKey.isBlank()) {
            String fallbackResult = callOpenAICompatible(prompt, targetLanguage);
            if (fallbackResult != null) {
                return fallbackResult;
            }
        }

        return "# Intelligence module unavailable. Please ensure MaaS service (port 8372) is running,\n"
             + "# or configure vlang.fallback.api.key in application.properties.\n"
             + "# Prompt was: " + prompt.lines().findFirst().orElse("(empty)");
    }

    // ──────────────────────────────────────────────
    // 主路径：调用 Intelligence /chat/sync 接口
    // ──────────────────────────────────────────────

    private String callIntelligence(String prompt, String targetLanguage) {
        String message = "Protocol: VLang Compiler\nTarget Language: " + targetLanguage + "\n\nIntent:\n" + prompt;
        String requestBody = "{"
                + "\"message\":\"" + jsonEscape(message) + "\","
                + "\"session_id\":\"vlang-compiler-session\","
                + "\"skill_names\":[],"
                + "\"summarize_title\":false"
                + "}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(intelligenceApiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                String status = extractJsonString(body, "status");
                String answer = extractJsonString(body, "answer");
                // 502 表示 MaaS 后端不通，直接触发降级
                String error = extractJsonString(body, "error");
                if (error != null && error.contains("502")) {
                    return null; // 触发降级
                }
                if ("success".equalsIgnoreCase(status) && answer != null && !answer.isBlank()) {
                    return extractCodeFromMarkdown(answer);
                }
            }
        } catch (Exception e) {
            // 连接失败，触发降级
        }
        return null;
    }

    // ──────────────────────────────────────────────
    // 降级路径：调用 OpenAI 兼容 API
    // ──────────────────────────────────────────────

    private String callOpenAICompatible(String prompt, String targetLanguage) {
        String systemPrompt = "You are VLang Compiler, a code generation engine. "
                + "Generate ONLY executable " + targetLanguage + " code, no explanation. "
                + "Wrap code in a single ```" + targetLanguage + "\\n...\\n``` block.";

        String requestBody = "{"
                + "\"model\":\"" + jsonEscape(fallbackModel) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + jsonEscape(systemPrompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}"
                + "],"
                + "\"temperature\":0.2,"
                + "\"max_tokens\":4096"
                + "}";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(fallbackApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + fallbackApiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                // 解析 OpenAI 格式: choices[0].message.content
                String content = extractOpenAIContent(body);
                if (content != null && !content.isBlank()) {
                    return extractCodeFromMarkdown(content);
                }
            }
        } catch (Exception e) {
            // 忽略，返回 null
        }
        return null;
    }

    /**
     * 从 OpenAI 响应中提取 choices[0].message.content
     */
    private String extractOpenAIContent(String json) {
        // 从 "content":"..." 提取（简化版，适用于非嵌套结构）
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return jsonUnescape(m.group(1));
        }
        return null;
    }

    /**
     * 从 Markdown 代码块中提取纯代码
     * 输入: ```python\nprint("hello")\n```
     * 输出: print("hello")
     */
    private String extractCodeFromMarkdown(String text) {
        if (text == null) return null;
        // 匹配 ```语言名\n代码\n```
        Pattern p = Pattern.compile("```(?:\\w+)?\\n([\\s\\S]*?)\\n?```");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).strip();
        }
        // 没有代码块则直接返回原文
        return text.strip();
    }

    public String healCode(String originalCode, String errorLog, String targetLanguage) {
        String prompt = "### VLang Self-Healing Protocol ###\n"
                + "The compilation for " + targetLanguage + " failed with the following error:\n"
                + "--- Error Log ---\n" + errorLog + "\n\n"
                + "--- Original Intent ---\n" + originalCode + "\n\n"
                + "Task: Analyze the error and generate FIXED executable "
                + targetLanguage + " code that resolves the issue while preserving intent.";
        return generateCode(prompt, targetLanguage);
    }

    // ──────────────────────────────────────────────
    // JSON 工具方法
    // ──────────────────────────────────────────────

    private static String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return null;
        return jsonUnescape(matcher.group(1));
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String jsonUnescape(String value) {
        if (value == null) return null;
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
