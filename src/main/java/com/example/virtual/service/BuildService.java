package com.example.virtual.service;

import com.example.virtual.domain.VirtualObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BuildService {

    private final VirtualObjectService objectService;
    private final ExecutionService executionService;

    /**
     * Builds a comprehensive binary for the project starting from the entry point.
     * This involves merging all generated C++/Native code from dependencies.
     */
    public String buildProject(Long entryId) {
        VirtualObject entry = objectService.getById(entryId);
        if (entry == null) return "Error: Entry point not found.";

        try {
            Path tempDir = Files.createTempDirectory("vlang_build_");
            Set<Long> processed = new HashSet<>();
            StringBuilder unifiedSource = new StringBuilder();

            // 1. Collect all code (Self + Dependencies)
            collectSource(entry, unifiedSource, processed);

            // 2. Link with runtime headers (if any)
            // For now, we just pass the unified source to the execution service
            return executionService.execute(unifiedSource.toString(), "cpp");

        } catch (IOException e) {
            return "Build Error: " + e.getMessage();
        }
    }

    private void collectSource(VirtualObject object, StringBuilder source, Set<Long> processed) {
        if (object == null || processed.contains(object.getId())) return;
        processed.add(object.getId());

        // Process dependencies first (bottom-up)
        if (object.getDependencyIds() != null) {
            for (Long depId : object.getDependencyIds()) {
                collectSource(objectService.getById(depId), source, processed);
            }
        }

        // Add the code for the current object
        // If it was already compiled to Native (C++), use that.
        // If it's pure C++, use the content.
        String codeToAdd = (object.getGeneratedCode() != null && !object.getGeneratedCode().isBlank()) 
                           ? object.getGeneratedCode() 
                           : object.getContent();
        
        if (codeToAdd != null && !codeToAdd.isBlank()) {
            source.append("\n// --- Source from: ").append(object.getName()).append(" ---\n");
            source.append(codeToAdd).append("\n");
        }
    }
}
