---
name: vlang-vllm-engine
description: AI-Native multi-language development and execution engine skill leveraging VLLM. Provides guidelines on the tree-object language architecture.
---

# VLang (Virtual Language) Engine 核心理念与架构规范

## 1. 核心理念 (Core Concept)
VLang 是一种旨在消除人机沟通隔阂的新型 AI 原生编程环境。在 `virtual` 模块中实现的核心目的，是允许开发者**在树状文件结构中，混合使用自然语言与原生代码（Java/Python等）来组织逻辑体系**。VLLM（大型语言模型推理服务）担任中间层的智能化“跨语言编译器”，使得大白话意图能够直接转换为机器认识的精准指令。

## 2. 核心架构与开发约束 (Development Patterns)

### 2.1 树状对象文件结构 (Tree-based Object Context)
- **文件即对象**: 系统中不再采用传统的扁平或者纯基于命名空间的包结构。使用严格的树状形态（Tree Structure）来维护多个文件，每一个节点（文件）都被视为一个独立运行时的 `对象 (Object)`。
- **上下文继承**: 子节点对象可以隐含继承获取父节点对象的上下文支持（如定义的公共变量或已注册的服务）。
- **用户心智模型**: 开发者在选中的节点文件内编写代码，实际上是在为这个确切的“对象”赋予属性或行为方法。

### 2.2 自然语言与多语言混合编写 (Natural & Native Mixed Language)
- **自然语言模式 (Natural Language Mode)**: 开发者写下“从数据库中查询今天过期的订单列表并将其状态置为0”。
- **传统语言模式 (Native Mode)**: 支持嵌入已有的 Python、Java。这种情境下，需要预先定义特定标识符以便解析引擎区分哪些部分是传统语言。
- **无缝转换**: 传统 Java/Python 代码无需模型介入将直接流转给底层 Sandbox；自然语言不仅能够生成独立逻辑，也被允许通过大模型生成可以与当前代码块中的 Python/Java 互通的数据接口绑定。

### 2.3 VLLM 翻译桥接器 (VLLM Transformation Layer)
当拦截到自然语言编写的文档段落时：
1. **组装 Prompt**: 模块在底层把当前 Tree 节点内的运行上下文（依赖、父级接口、期待的返回值类型等）以及自然语言文档，打包并注入到强规范的 System Prompt 中。
2. **生成目标机器代码**: VLLM 需要被严格约束以吐出不带 markdown 但可直接由底层沙箱解析运行的代码。
3. **AST与执行缓存 (Cache & Abstract Syntax Tree)**: 对经 VLLM 生成的可靠代码生成指纹记录并缓存。当同样意图并且同样上下文被再次触发时，跳过大模型调用，直接利用运行时沙箱重播。

## 3. 实现指南 (Implementation Guide)
在具体的代码落地过程中，要求遵循以下步骤与原则：
1. **统一扩展名与解析拦截**: 为此语言建立统一格式（如 `.vlang` 后缀），其需要有能力进行 YAML/JSON 或特定语法的元数据头设置。
2. **前后端接口匹配**: 前端必须支持一套树形层级的项目文件视图，且允许拖拽与层级重组。
3. **沙箱隔离隔离**: 为防范由于 VLLM 幻觉产生出的毁灭性系统命令代码，需配置严格安全级的沙箱执行环境用于测试 VLLM 输出结果。
