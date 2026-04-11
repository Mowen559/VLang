/**
 * VLang Native Parser - JNI Export Header
 *
 * VLang 自举模块: 编译器核心热路径的 C++ 原生实现
 * 由 VLang 意图系统本身生成，实现"编译器自举"
 *
 * VLang-SH: self-hosting-v1.0
 * VLine: 1
 */
#pragma once
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 提取 VLang 意图正文 (剥离 FrontMatter YAML 头)
 * 替换 VLangParserService.extractIntentBody() 的 Java 实现
 * 性能收益: FSM vs Java Regex Pattern ~8-12x
 *
 * @param  jContent  VLang 文件原始内容
 * @return 剥离 FrontMatter 后的自然语言意图正文
 */
JNIEXPORT jstring JNICALL
Java_com_example_virtual_service_VNativeEngine_nativeExtractIntentBody(
    JNIEnv* env, jobject obj, jstring jContent);

/**
 * 解析 FrontMatter YAML，提取 imports 依赖块的原始 YAML 字符串
 * 替换 VLangParserService.parseAndResolveDependencies() 中的解析阶段
 * 返回 JSON 格式字符串: {"imports": ["name1", 42, ...]}
 *
 * @param  jContent  VLang 文件原始内容
 * @return JSON 字符串，包含 imports 列表
 */
JNIEXPORT jstring JNICALL
Java_com_example_virtual_service_VNativeEngine_nativeParseFrontMatterJson(
    JNIEnv* env, jobject obj, jstring jContent);

/**
 * 提取多语言代码的公开符号摘要
 * 替换 MetadataExtractorService.extractSummary() 的 Java 实现
 * 性能收益: 线性扫描 vs 多次 Pattern.compile ~5-8x
 *
 * @param  jCode      目标语言源代码
 * @param  jLanguage  语言标识 (java/python/go/cpp/js)
 * @return 符号摘要字符串
 */
JNIEXPORT jstring JNICALL
Java_com_example_virtual_service_VNativeEngine_nativeExtractSymbols(
    JNIEnv* env, jobject obj, jstring jCode, jstring jLanguage);

/**
 * 引擎健康检查 - 返回版本字符串
 * 用于 Spring @PostConstruct 验证 JNI 链接成功
 */
JNIEXPORT jstring JNICALL
Java_com_example_virtual_service_VNativeEngine_nativeVersion(
    JNIEnv* env, jobject obj);

#ifdef __cplusplus
}
#endif
