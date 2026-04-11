# VLang

VLang is an intent-driven programming workflow that lets you describe requirements in natural language and compile them into executable code (Python / Java / Go / C++), with dependency linking, semantic debugging, and optional native acceleration.

## What It Is

This repository contains a Spring Boot service that provides:

- `VirtualObject` tree storage (file/folder model)
- VLang FrontMatter parsing (`imports` dependencies)
- Natural-language intent -> target language code generation
- Runtime execution (Python / Java / Go / C++)
- Build pipeline for dependency-linked project assembly
- Semantic debugging (`// VLine: N` mapping back to intent)
- Self-healing attempt for C++ compile failures
- Native parser bootstrap (JNI + C++) with Java fallback

## Core Value

- Faster prototype-to-code loop: write intent first, generate runnable code quickly.
- Better traceability: generated code is required to include VLine markers for intent-level diagnostics.
- Dependency-aware composition: FrontMatter imports allow object-level modular development.
- Engineering resilience: native parser path improves performance, while Java fallback keeps service available.
- Practical execution model: from generation to run/build in a single API workflow.

## VLang Usage (Recommended Flow)

### 1. Create an Intent Object

Create a `natural` language object with optional FrontMatter:

```yaml
---
imports:
  - 2
  - common-utils
---
Build a user registration function with password validation and structured logging.
```

`imports` supports:

- Object IDs (e.g. `2`)
- Object names (e.g. `common-utils`, resolved by fuzzy name matching)

### 2. Compile Intent to Target Code

Call compile endpoint with target language:

- `POST /api/virtual/objects/{id}/compile?targetLanguage=python`
- `POST /api/virtual/objects/{id}/compile?targetLanguage=cpp`

Generated code is stored into `generatedCode` and object status is updated.

### 3. Run Code

Run compiled/generated content:

- `POST /api/virtual/objects/{id}/run?language=python`
- `POST /api/virtual/objects/{id}/run?language=cpp`

If object language is `natural` and not yet compiled, the service auto-compiles first (default to Python).

### 4. Build Linked Project

For dependency-linked objects:

- `POST /api/virtual/objects/{id}/build`

The service collects source from self + dependencies and executes a unified build/run path.

## API Quick Reference

Base route: `/api/virtual/objects`

- `GET /api/virtual/objects?parentId=` list children
- `POST /api/virtual/objects` create object
- `PUT /api/virtual/objects/{id}` update object
- `DELETE /api/virtual/objects/{id}` delete object
- `POST /api/virtual/objects/{id}/compile?targetLanguage=...` compile
- `POST /api/virtual/objects/{id}/run?language=...` run
- `POST /api/virtual/objects/{id}/build` project build
- `GET /api/virtual/objects/bootstrap/status` native engine status
- `POST /api/virtual/objects/bootstrap/trigger` trigger native bootstrap

## Local Run

### Requirements

- JDK 17
- Maven 3.9+
- Optional: Python / Go / MinGW g++ / Docker (for multi-language execution paths)

### Start

```bash
mvn spring-boot:run
```

Default service port: `8082`

## Config

See `src/main/resources/application.properties`.

Important settings:

- `intelligence.api.url` primary code-generation endpoint
- `vlang.fallback.api.url` OpenAI-compatible fallback endpoint
- `vlang.fallback.api.key` fallback API key
- `vlang.fallback.api.model` fallback model name

## Native Bootstrap Notes

VLang attempts to compile and load native parser code at startup (`VNativeEngine`).

- success: status returns `NATIVE_READY`
- fail: service falls back to Java parser path (`JAVA_FALLBACK`)

This means the system remains usable even without native toolchain.

## Common Git Declarations (Conventional Commits)

Recommended commit prefixes:

- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only
- `refactor:` code restructuring without behavior change
- `test:` tests added/updated
- `chore:` tooling/build/maintenance

Examples:

```bash
git commit -m "feat: add VLang FrontMatter dependency resolution"
git commit -m "fix: fallback to Java parser when native bootstrap fails"
git commit -m "docs: improve README with VLang workflow and API examples"
```

## Typical Git Flow For This Repo

```bash
git checkout -b docs/readme-vlang-guide
git add README.md
git commit -m "docs: add VLang usage and value overview"
git push -u origin docs/readme-vlang-guide
```

If you commit directly to `main`:

```bash
git add README.md
git commit -m "docs: add VLang usage and value overview"
git push origin main
```
