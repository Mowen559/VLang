package com.example.virtual.controller;

import com.example.virtual.domain.VirtualObject;
import com.example.virtual.service.BuildService;
import com.example.virtual.service.CompilerService;
import com.example.virtual.service.ExecutionService;
import com.example.virtual.service.VNativeEngine;
import com.example.virtual.service.VirtualObjectService;
import com.example.virtual.service.VZeroCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/virtual/objects")
@RequiredArgsConstructor
public class VirtualObjectController {

    private final VirtualObjectService service;
    private final CompilerService compilerService;
    private final ExecutionService executionService;
    private final BuildService buildService;
    private final VNativeEngine nativeEngine;

    @GetMapping
    public List<VirtualObject> getChildren(@RequestParam(required = false) Long parentId) {
        return service.getChildren(parentId);
    }

    @PostMapping
    public VirtualObject create(@RequestBody VirtualObject object) {
        return service.create(object);
    }

    @PutMapping("/{id}")
    public VirtualObject update(@PathVariable Long id, @RequestBody VirtualObject object) {
        return service.update(id, object);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/compile")
    public String compile(@PathVariable Long id, @RequestParam String targetLanguage) {
        return compilerService.compile(id, targetLanguage);
    }

    @PostMapping("/{id}/build")
    public String build(@PathVariable Long id) {
        return buildService.buildProject(id);
    }

    @GetMapping("/test/value")
    public int getTestValue() {
        return 100;
    }

    /**
     * VLang 自举状态查询
     * 返回 native 引擎状态：NATIVE_READY / BOOTSTRAPPING / JAVA_FALLBACK
     */
    @GetMapping("/bootstrap/status")
    public String getBootstrapStatus() {
        return nativeEngine.getStatusReport();
    }

    /**
     * 手动触发自举（热更新场景：用户修改了 __vlang_native_parser_src__ 后调用）
     */
    @PostMapping("/bootstrap/trigger")
    public String triggerBootstrap() {
        nativeEngine.bootstrapAsync();
        return "{\"status\":\"BOOTSTRAP_TRIGGERED\",\"message\":\"Native engine recompilation started asynchronously.\"}";
    }

    private final VZeroCopyService zeroCopyService;

    @PostMapping("/{id}/demo/zero-copy")
    public String demoZeroCopy(@PathVariable Long id) {
        // 1. 在 Java 层开辟一块“工业级图像数据”内存 (假设 1MB)
        String memId = zeroCopyService.allocate(1024 * 1024);
        long address = zeroCopyService.getPhysicalAddress(memId);
        
        // 2. 模拟写入一些特征数据
        byte[] rawData = "VLANG_ZERO_COPY_PAYLOAD_DATA".getBytes();
        zeroCopyService.writeData(memId, rawData);
        
        // 3. 构造一个包含“物理缺陷”的 VLang 意图 (故意注入会导致 GCC 报错的指令，测试自愈)
        String vlangIntent = "--- system: zero-cpy-demo ---\n" +
                            "Using V_BUS_ACCESS at physical address " + address + ",\n" +
                            "read the data and print the payload length.\n" +
                            "// INJECTING FAULT: missing_header_include_here_error();";

        // 4. 执行并观察【内存透传 -> 编译报错 -> 自愈修复 -> 最终成功】的全流程
        return executionService.execute(vlangIntent, "cpp");
    }

    @PostMapping("/{id}/run")
    public String run(@PathVariable Long id, @RequestParam String language) {
        VirtualObject object = service.getById(id);
        if (object == null) return "Object not found";

        // Auto-compile if it's natural language and no code generated yet
        if ("natural".equalsIgnoreCase(object.getLanguage()) &&
            (object.getGeneratedCode() == null || object.getGeneratedCode().isEmpty())) {
            compilerService.compile(id, "python"); // Default to python for AI generation
            object = service.getById(id); // Reload object with generated code
        }

        // If it was a natural language file, run the generated code.
        // Otherwise run the raw content.
        String codeToRun = (object.getGeneratedCode() != null && !object.getGeneratedCode().isEmpty())
                           ? object.getGeneratedCode() : object.getContent();

        // If it's still natural but we are running it, try to treat language as python
        String executionLanguage = "natural".equalsIgnoreCase(language) ? "python" : language;

        return executionService.execute(codeToRun, executionLanguage);
    }
}
