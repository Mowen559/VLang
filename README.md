# VLang

VLang 是一个将自然语言意图转化为机器可执行代码的编程语言体系。它不是单纯的代码生成器，而是一条从“人类表达”到“机器执行”的完整工程链路。

## 项目声明

VLang 被定义为一种“自然语言 -> 机器语言”的新型高级编程语言实践。

在项目愿景上，我们将其定位为目前最贴近人类自然交流方式的高级编程语言方向之一：

- 人说需求，不必先写语法
- 系统理解意图，自动编译为目标代码
- 代码可执行、可调试、可追溯到原始意图

这意味着编程入口从“语法优先”转向“意图优先”，让表达与实现更加接近。

## 目前已实现的核心能力

- 自然语言对象建模（`language = natural`）
- FrontMatter 依赖解析（`imports`）
- 多目标编译（Python / Java / Go / C++）
- 一键执行与项目构建（含依赖合并）
- 语义调试映射（`// VLine: N` 回溯到意图行）
- C++ 编译失败后的自愈尝试
- Native 引擎自举 + Java 回退兜底

## 语言自举（Self-Hosting Bootstrap）

VLang 在服务启动后会触发 native 引擎自举流程：

1. 异步编译并加载 JNI Native Parser
2. 成功后进入 `NATIVE_READY`
3. 若 native 失败，自动降级到 `JAVA_FALLBACK`

这样可以在追求性能的同时，保证系统可用性与稳定性。

## 启动后的静态页面

项目已包含启动后可直接访问的静态页面入口：

- `src/main/resources/static/index.html`

它可作为当前版本的可视化入口，后续可以扩展为完整的 VLang IDE / 运维控制台。

## 快速使用方式

### 1. 创建自然语言意图对象

示例（支持 FrontMatter 依赖）：

```yaml
---
imports:
  - 2
  - common-utils
---
实现一个带日志与参数校验的用户注册逻辑。
```

### 2. 编译到目标语言

- `POST /api/virtual/objects/{id}/compile?targetLanguage=python`
- `POST /api/virtual/objects/{id}/compile?targetLanguage=cpp`

### 3. 运行代码

- `POST /api/virtual/objects/{id}/run?language=python`
- `POST /api/virtual/objects/{id}/run?language=cpp`

### 4. 依赖合并构建

- `POST /api/virtual/objects/{id}/build`

## 当前不足（诚实边界）

VLang 已打通主链路，但距离成熟生产语言仍有差距：

- 工具链环境依赖较强，跨平台一致性还需增强
- 执行安全沙箱、资源隔离、审计能力仍需完善
- FrontMatter 与语义解析能力仍偏实用版，复杂场景待强化
- 当前存储层较轻量，生产级数据治理方案待升级
- 生成质量受模型与上下文影响，评测与回归体系仍需持续建设
- 生态仍在早期，标准库、插件体系与规范文档待完善

## 常用 Git 声明方式（Conventional Commits）

推荐前缀：

- `feat:` 新功能
- `fix:` 问题修复
- `docs:` 文档变更
- `refactor:` 重构
- `test:` 测试相关
- `chore:` 工程与维护

示例：

```bash
git commit -m "feat: add VLang FrontMatter dependency resolution"
git commit -m "fix: fallback to Java parser when native bootstrap fails"
git commit -m "docs: polish README with VLang declaration and limitations"
```

## 本地运行

要求：

- JDK 17
- Maven 3.9+
- 可选：Python / Go / MinGW g++ / Docker

启动：

```bash
mvn spring-boot:run
```

默认端口：`8082`

## 关键配置

见 `src/main/resources/application.properties`：

- `intelligence.api.url` 主代码生成服务地址
- `vlang.fallback.api.url` 兼容 OpenAI 的降级地址
- `vlang.fallback.api.key` 降级模型 API Key
- `vlang.fallback.api.model` 降级模型名称
