package com.example.virtual.service;

import groovy.lang.GroovyShell;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final DependencyAnalyst dependencyAnalyst;
    private final ContainerBuildEngine containerBuildEngine;
    private final VDebugService vDebugService;
    private final VLLMService vllmService;

    // 命令和路径配置（与系统安装一致）
    private static final String PYTHON_PATH = resolvePython();
    private static final String GPP_PATH    = "F:\\2\\mingw\\mingw64\\bin\\g++.exe";
    private static final String GO_PATH     = resolveGo();

    /**
     * 自动检测 Python 可执行文件的真实路径。
     * Java ProcessBuilder 不继承 WindowsApps 中的虚拟映射，必须用绝对路径。
     */
    private static String resolvePython() {
        // 已知安装位置（高优先级）
        String[] knownPaths = {
            "D:\\python\\install\\python.exe",
            "C:\\Python312\\python.exe",
            "C:\\Python311\\python.exe",
            "C:\\Python310\\python.exe",
            System.getenv("USERPROFILE") + "\\AppData\\Local\\Programs\\Python\\Python312\\python.exe",
            System.getenv("USERPROFILE") + "\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
        };
        for (String path : knownPaths) {
            if (path != null && new java.io.File(path).exists()) {
                return path;
            }
        }
        // 最后回退：尝试能调起 py.exe Launcher
        if (new java.io.File("C:\\Windows\\py.exe").exists()) return "C:\\Windows\\py.exe";
        return "python"; // 最后降级到 PATH（可能失败）
    }

    private static String resolveGo() {
        String[] knownPaths = {
            "C:\\Program Files\\Go\\bin\\go.exe",
            "C:\\Go\\bin\\go.exe"
        };
        for (String path : knownPaths) {
            if (new java.io.File(path).exists()) return path;
        }
        return "go";
    }

    public String execute(String code, String language) {
        // ... (保持现有分发逻辑)
        var dependencies = dependencyAnalyst.analyzerDependencies(code, language);
        if (dependencyAnalyst.requiresContainer(dependencies, language)) {
            String result = containerBuildEngine.buildInContainer(code, language, dependencies);
            if (result.contains("Error")) {
                return vDebugService.mapErrorToIntent(result, code, language);
            }
            return result;
        }

        return switch (language.toLowerCase()) {
            case "java" -> executeJava(code);
            case "python" -> executePython(code, code); // 传入源码用于 Debug
            case "go" -> executeGo(code);
            case "cpp", "c" -> executeCpp(code, code); // 传入源码用于 Debug
            default -> "Unsupported language for execution: " + language;
        };
    }

    private String executePython(String code, String originalVSource) {
        try {
            Path tempFile = Files.createTempFile("vlang_", ".py");
            Files.writeString(tempFile, code);
            ProcessBuilder pb = new ProcessBuilder(PYTHON_PATH, tempFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            
            if (process.waitFor() != 0) {
                return vDebugService.mapErrorToIntent(output, originalVSource, "python");
            }
            Files.deleteIfExists(tempFile);
            return output;
        } catch (Exception e) {
            return "Python Execution Error: " + e.getMessage() + " (Python path: " + PYTHON_PATH + ")";
        }
    }

    private String executeJava(String code) {
        try {
            GroovyShell shell = new GroovyShell();
            Object result = shell.evaluate(code);
            return result != null ? result.toString() : "Success (no return value)";
        } catch (Exception e) {
            return "Java Execution Error: " + e.getMessage();
        }
    }

    private String executeGo(String code) {
        try {
            Path tempDir = Files.createTempDirectory("vlang_go");
            Path mainGo = tempDir.resolve("main.go");
            Files.writeString(mainGo, code);
            ProcessBuilder pb = new ProcessBuilder(GO_PATH, "run", mainGo.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            
            if (process.waitFor() != 0) {
                return vDebugService.mapErrorToIntent(output, code, "go");
            }
            return output;
        } catch (Exception e) {
            return "Go Execution Error: " + e.getMessage();
        }
    }

    private String executeCpp(String code, String originalVSource) {
        try {
            Path tempDir = Files.createTempDirectory("vlang_cpp");
            Path srcFile = tempDir.resolve("main.cpp");
            Path exeFile = tempDir.resolve("app.exe");
            Files.writeString(srcFile, code);
            
            ProcessBuilder cp = new ProcessBuilder(
                GPP_PATH,
                srcFile.toString(),
                "-O3", "-static",
                "-Wl,--subsystem,console",
                "-o", exeFile.toString()
            );
            cp.redirectErrorStream(true);
            Process compileProcess = cp.start();
            String compileOutput = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            
            if (compileProcess.waitFor() != 0) {
                // [SELF-HEALING] 激活自愈逻辑
                String healedVSource = vllmService.healCode(originalVSource, compileOutput, "cpp");
                if (healedVSource != null && !healedVSource.equals(originalVSource)) {
                     // 递归尝试一次修复后的执行 (此处简化为直接返回，实际可做多层递归)
                     return " [SELF-HEALED ATTEMPT] \n" + executeCpp(healedVSource, originalVSource);
                }
                return vDebugService.mapErrorToIntent(compileOutput, originalVSource, "cpp");
            }
            
            ProcessBuilder rp = new ProcessBuilder(exeFile.toString());
            rp.redirectErrorStream(true);
            Process runProcess = rp.start();
            return new BufferedReader(new InputStreamReader(runProcess.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "VLang Execution Error: " + e.getMessage();
        }
    }
}
