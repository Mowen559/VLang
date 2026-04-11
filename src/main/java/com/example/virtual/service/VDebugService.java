package com.example.virtual.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义调试服务：负责将物理编译报错映射回 VLang 自然语言意图
 */
@Service
@Slf4j
public class VDebugService {

    // 匹配代码中的行号注释，例如 // VLine: 5
    private static final Pattern VLINE_MARKER = Pattern.compile("// VLine:\\s*(\\d+)");
    
    // 匹配 GCC 错误输出，例如 main.cpp:20:1: error: ...
    private static final Pattern GCC_ERROR = Pattern.compile("([^:]+):(\\d+):(\\d+):\\s*(error|warning):(.*)");
    
    // 匹配 Python 错误堆栈，例如 File "main.py", line 15, in <module>
    private static final Pattern PY_ERROR = Pattern.compile("File \".*\", line (\\d+), in (.*)");

    /**
     * 解析错误输出并进行语义重定向
     */
    public String mapErrorToIntent(String rawError, String generatedCode, String language) {
        StringBuilder diagnostic = new StringBuilder("--- VDebug Semantic Diagnosis ---\n");
        String[] lines = rawError.split("\n");
        boolean mapped = false;

        for (String line : lines) {
            Matcher errorMatcher = language.equalsIgnoreCase("python") ? PY_ERROR.matcher(line) : GCC_ERROR.matcher(line);
            
            if (errorMatcher.find()) {
                int physicalLine = Integer.parseInt(errorMatcher.group(1));
                String message = errorMatcher.group(errorMatcher.groupCount()); // 最后一个分组通常是错误描述
                
                // 在生成的代码中寻找最近的语义锚点
                int intentLine = findNearestIntentLine(generatedCode, physicalLine);
                if (intentLine > 0) {
                    diagnostic.append(String.format("[Intent Line %d] Physics Error: %s\n", intentLine, message.trim()));
                    mapped = true;
                }
            }
        }

        if (!mapped) {
            diagnostic.append("Original Error: \n").append(rawError);
        }
        
        return diagnostic.toString();
    }

    private int findNearestIntentLine(String generatedCode, int physicsLineNumber) {
        String[] codeLines = generatedCode.split("\n");
        // 向上寻找最近的 // VLine: N 注释
        for (int i = Math.min(physicsLineNumber - 1, codeLines.length - 1); i >= 0; i--) {
            Matcher m = VLINE_MARKER.matcher(codeLines[i]);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return -1;
    }
}
