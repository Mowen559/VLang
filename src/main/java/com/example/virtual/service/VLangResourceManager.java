package com.example.virtual.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 工业化产物管理器：负责编译产物的持久化、归档以及环境预热
 */
@Service
@Slf4j
public class VLangResourceManager {

    private final Path artifactRoot;

    public VLangResourceManager() throws IOException {
        // 工业化路径：用户文档下的 VLang 产物仓
        this.artifactRoot = Path.of(System.getProperty("user.home"), ".vlang", "artifacts");
        if (!Files.exists(artifactRoot)) {
            Files.createDirectories(artifactRoot);
        }
    }

    /**
     * 将编译出的二进制文件推送到持久化仓库
     */
    public String storeArtifact(Path sourceFile, String objectName, String language) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("%s_%s.%s", objectName, timestamp, getExtension(language));
            Path targetPath = artifactRoot.resolve(fileName);
            
            Files.copy(sourceFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Artifact stored successfully at: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("Failed to store artifact", e);
            return null;
        }
    }

    /**
     * 环境预热：预先拉取核心工业镜像
     */
    public void warmupEnvironments() {
        String[] coreImages = {"gcc:15.2", "python:3.12-slim", "maven:3.9-eclipse-temurin-21"};
        for (String image : coreImages) {
            new Thread(() -> {
                try {
                    log.info("Warming up base image: {}", image);
                    new ProcessBuilder("docker", "pull", image).start().waitFor();
                } catch (Exception e) {
                    log.warn("Warmup failed for image: {}", image);
                }
            }).start();
        }
    }

    private String getExtension(String language) {
        return switch (language.toLowerCase()) {
            case "cpp" -> "exe";
            case "java" -> "jar";
            case "python" -> "py";
            default -> "bin";
        };
    }
}
