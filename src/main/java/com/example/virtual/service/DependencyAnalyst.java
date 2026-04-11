package com.example.virtual.service;

import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依赖分析器：从生成的代码中自动提取所需的三方库
 */
@Service
public class DependencyAnalyst {

    private static final Pattern JAVA_IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([a-zA-Z0-9._]+);", Pattern.MULTILINE);
    private static final Pattern PYTHON_IMPORT = Pattern.compile("^(?:from|import)\\s+([a-zA-Z0-9._]+)", Pattern.MULTILINE);
    private static final Pattern CPP_INCLUDE = Pattern.compile("^#include\\s*[<\"]([a-zA-Z0-9._/]+)[>\"]", Pattern.MULTILINE);

    public Set<String> analyzerDependencies(String code, String language) {
        Set<String> dependencies = new HashSet<>();
        if (code == null || code.isBlank()) return dependencies;

        Pattern pattern = switch (language.toLowerCase()) {
            case "java" -> JAVA_IMPORT;
            case "python" -> PYTHON_IMPORT;
            case "cpp", "c" -> CPP_INCLUDE;
            default -> null;
        };

        if (pattern != null) {
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                String dep = matcher.group(1).trim();
                // 过滤掉系统标准库（基础过滤逻辑）
                if (!isStandardLibrary(dep, language)) {
                    dependencies.add(dep);
                }
            }
        }
        return dependencies;
    }

    private boolean isStandardLibrary(String dep, String language) {
        return switch (language.toLowerCase()) {
            case "java" -> dep.startsWith("java.") || dep.startsWith("javax.");
            case "python" -> dep.equals("os") || dep.equals("sys") || dep.equals("math") || dep.equals("time") || dep.equals("json");
            case "cpp", "c" -> dep.equals("iostream") || dep.equals("vector") || dep.equals("string") || dep.equals("map") || dep.equals("algorithm");
            default -> false;
        };
    }

    /**
     * 判断是否属于“重型”依赖，需要触发容器构建
     */
    public boolean requiresContainer(Set<String> dependencies, String language) {
        for (String dep : dependencies) {
            if (isHeavyweight(dep, language)) return true;
        }
        return false;
    }

    private boolean isHeavyweight(String dep, String language) {
        return switch (language.toLowerCase()) {
            case "java" -> dep.contains("spring") || dep.contains("mybatis") || dep.contains("hibernate");
            case "python" -> dep.contains("torch") || dep.contains("tensorflow") || dep.contains("pandas") || dep.contains("sklearn");
            case "cpp" -> dep.contains("boost") || dep.contains("opencv") || dep.contains("qt");
            default -> false;
        };
    }
}
