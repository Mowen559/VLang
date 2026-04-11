package com.example.virtual.service;

import com.example.virtual.domain.VirtualObject;
import com.example.virtual.repository.VirtualObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VirtualObjectService {

    private final VirtualObjectRepository repository;
    private final VLangParserService vlangParser;

    public List<VirtualObject> getChildren(Long parentId) {
        return repository.findByParentId(parentId);
    }

    public VirtualObject create(VirtualObject object) {
        updateHashAndStatus(object);
        return repository.save(object);
    }

    public VirtualObject update(Long id, VirtualObject updated) {
        VirtualObject existing = repository.findById(id).orElseThrow();
        existing.setName(updated.getName());
        existing.setContent(updated.getContent());
        existing.setLanguage(updated.getLanguage());
        existing.setParentId(updated.getParentId());
        existing.setDependencyIds(updated.getDependencyIds());
        
        updateHashAndStatus(existing);
        return repository.save(existing);
    }

    private void updateHashAndStatus(VirtualObject object) {
        // 如果是 VLang 文件，自动解析 FrontMatter 中的依赖关系
        if ("natural".equalsIgnoreCase(object.getLanguage())) {
            List<Long> resolvedIds = vlangParser.parseAndResolveDependencies(object.getContent());
            object.setDependencyIds(resolvedIds);
        }

        String newHash = calculateHash(object);
        if (!newHash.equals(object.getContentHash())) {
            object.setContentHash(newHash);
            // 内容发生变化，标记为需要重新编译
            object.setBuildStatus(VirtualObject.BuildStatus.STALE);
        }
    }

    private String calculateHash(VirtualObject object) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String data = (object.getContent() != null ? object.getContent() : "") + 
                         (object.getLanguage() != null ? object.getLanguage() : "") +
                         (object.getDependencyIds() != null ? object.getDependencyIds().toString() : "[]");
            byte[] encodedhash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public VirtualObject getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 按名称模糊查找 VirtualObject（用于幂等检查与依赖解析）
     */
    public List<VirtualObject> findByName(String name) {
        return repository.findByName(name);
    }
}
