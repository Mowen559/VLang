package com.example.virtual.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 容器化构建引擎：负责生成构建环境并分发给 Docker
 */
@Service
@Slf4j
public class ContainerBuildEngine {

    /**
     * 执行容器化构建（工业化版本：带增量缓存）
     */
    public String buildInContainer(String code, String language, Set<String> dependencies) {
        try {
            // 1. 生成依赖指纹 (Dependency Hash)
            String depHash = calculateDependencyHash(dependencies, language);
            String baseImageName = "vlang-base-" + language.toLowerCase() + "-" + depHash;

            Path workDir = Files.createTempDirectory("vlang_industrial_build_");
            log.info("Industrial build started. Hash: {}, WorkDir: {}", depHash, workDir);

            // 2. 检查基础环境镜像是否存在
            if (!checkImageExists(baseImageName)) {
                log.info("Base image cache miss. Building base environment: {}", baseImageName);
                buildBaseImage(workDir, language, dependencies, baseImageName);
            } else {
                log.info("Base image cache hit: {}", baseImageName);
            }

            // 3. 在基础环境镜像之上进行轻量级应用打包
            return runBuildAndExecute(workDir, code, language, baseImageName);

        } catch (Exception e) {
            log.error("Industrial container build failed", e);
            return "Industrial Build Error: " + e.getMessage();
        }
    }

    private String calculateDependencyHash(Set<String> dependencies, String language) throws Exception {
        // 使用 TreeSet 保证顺序一致
        String sortedDeps = new TreeSet<>(dependencies).toString();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(sortedDeps.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < 8; i++) { // 只取前16位作为短哈希
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private boolean checkImageExists(String imageName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "images", "-q", imageName);
            Process proc = pb.start();
            String output = new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine();
            return output != null && !output.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private void buildBaseImage(Path workDir, String language, Set<String> dependencies, String imageName) throws Exception {
        String dockerfile = switch (language.toLowerCase()) {
            case "python" ->
                "FROM python:3.12-slim\n" +
                "RUN pip install --no-cache-dir " + String.join(" ", dependencies) + "\n" +
                "WORKDIR /app";
            case "java" ->
                "FROM maven:3.9-eclipse-temurin-21\n" +
                "RUN echo 'Caching dependencies for " + dependencies + "' > /cache-meta.txt\n" +
                "WORKDIR /app";
            default -> "FROM gcc:15.2\nWORKDIR /app";
        };
        Files.writeString(workDir.resolve("Dockerfile.base"), dockerfile);
        
        ProcessBuilder pb = new ProcessBuilder("docker", "build", "-f", "Dockerfile.base", "-t", imageName, ".");
        pb.directory(workDir.toFile());
        executeCommand(pb, "Base Image Build");
    }

    private String runBuildAndExecute(Path workDir, String code, String language, String baseImage) throws Exception {
        String fileName = language.equalsIgnoreCase("java") ? "Main.java" : "main.py";
        Files.writeString(workDir.resolve(fileName), code);

        String appDockerfile = "FROM " + baseImage + "\nCOPY . /app\n";
        appDockerfile += switch (language.toLowerCase()) {
            case "java" -> "RUN javac Main.java\nCMD [\"java\", \"Main\"]";
            case "python" -> "CMD [\"python\", \"main.py\"]";
            default -> "RUN g++ main.cpp -o app\nCMD [\"./app\"]";
        };
        Files.writeString(workDir.resolve("Dockerfile"), appDockerfile);

        String appImage = "vlang-app-" + System.currentTimeMillis();
        executeCommand(new ProcessBuilder("docker", "build", "-t", appImage, "."), "App Packaging");

        ProcessBuilder runPb = new ProcessBuilder("docker", "run", "--rm", appImage);
        String result = executeCommand(runPb, "App Execution");

        // 清理临时应用镜像
        new ProcessBuilder("docker", "rmi", appImage).start();

        return result;
    }

    private String executeCommand(ProcessBuilder pb, String context) throws Exception {
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new BufferedReader(new InputStreamReader(proc.getInputStream()))
                .lines().collect(Collectors.joining("\n"));
        if (proc.waitFor() != 0) {
            throw new RuntimeException(context + " failed:\n" + output);
        }
        return output;
    }
}
