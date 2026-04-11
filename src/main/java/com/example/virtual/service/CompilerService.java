package com.example.virtual.service;

import com.example.virtual.domain.VirtualObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompilerService {

    private final VLLMService vllmService;
    private final VirtualObjectService objectService;
    private final MetadataExtractorService metadataExtractor;
    private final BridgeTemplateService bridgeTemplateService;
    private final VLangParserService vlangParser;

    /**
     * Translates VLang intent to target-ready code with full dependency context.
     */
    public String compile(Long objectId, String targetLanguage) {
        VirtualObject object = objectService.getById(objectId);
        if (object == null) return null;

        // 清洗意图：剥离 YAML 元数据头，仅保留自然语言指令
        String intentBody = vlangParser.extractIntentBody(object.getContent());

        // 构建上下文摘要与桥接指南
        StringBuilder contextBuilder = new StringBuilder();
        StringBuilder bridgeBuilder = new StringBuilder();
        
        if (object.getDependencyIds() != null) {
            contextBuilder.append("\n--- Shared Interfaces & Context ---\n");
            for (Long depId : object.getDependencyIds()) {
                VirtualObject dep = objectService.getById(depId);
                if (dep != null) {
                    contextBuilder.append("Dependency [").append(dep.getName()).append("]:\n");
                    contextBuilder.append(metadataExtractor.extractSummary(dep.getContent(), dep.getLanguage()));
                    contextBuilder.append("\n");
                    
                    // 获取对应语言的桥接指南
                    bridgeBuilder.append(bridgeTemplateService.getRewriteGuidance(dep.getLanguage())).append("\n");
                }
            }
        }

        // 工业化 Prompt 增强：注入日志规范与版本追踪要求
        String prompt = String.format(
            "VLang Industrial Compiler Protocol\n" +
            "Task: Generate high-performance code for [%s] in [%s].\n" +
            "User Intent: \"%s\"\n\n" +
            "--- Industrial Constraints ---\n" +
            "1. Logging: Use standard logging (SLF4J for Java, logging for Python). Log key execution steps.\n" +
            "2. Versioning: Include a comment with 'VLang-SH:%s'.\n" +
            "3. Debugging: CRITICAL! For every logical block, add a comment line '// VLine: N' where N is the corresponding line number from User Intent.\n" +
            "4. Interoperability: %s\n" + // 数据映射指南
            "5. Entry Point: If this is a main component, implement 'int main()' or 'public static void main'.\n" +
            "6. Linkage: Use static linkage logic for a standalone binary.\n\n" +
            "--- Context & Dependencies ---\n%s\n" +
            "--- Technical Bridge FFI ---\n%s\n" + // 高级 FFI 模板
            "--- Target-Ready Code ---\n",
            object.getName(), 
            targetLanguage, 
            intentBody,
            object.getContentHash().substring(0, 8),
            bridgeTemplateService.getDataMappingGuidance(object.getLanguage()),
            contextBuilder.toString(),
            bridgeTemplateService.getBridgeTemplate(object.getLanguage(), targetLanguage)
        );
        
        String generated = vllmService.generateCode(prompt, targetLanguage);
        
        // 更新对象状态
        object.setGeneratedCode(generated);
        object.setBuildStatus(VirtualObject.BuildStatus.RELIABLE);
        object.setCompiledHash(object.getContentHash());
        objectService.update(objectId, object);
        
        return generated;
    }
}
