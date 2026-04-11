package com.example.virtual.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class VirtualObject implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private Long parentId;

    private ObjectType type;

    private String language; // natural, java, python, go, cpp, etc.

    private String content;
    private String generatedCode;

    // 新增：用于增量编译的 Hash 校验
    private String contentHash;
    private String compiledHash;

    // 新增：构建状态
    private BuildStatus buildStatus;

    // 新增：项目内依赖关联
    private java.util.List<Long> dependencyIds = new java.util.ArrayList<>();

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public enum ObjectType {
        FILE, DIRECTORY
    }

    public enum BuildStatus {
        STALE,      // 内容已变动，需要重编
        RELIABLE,   // 已编译，内容未变动
        FAILED      // 编译失败
    }
}
