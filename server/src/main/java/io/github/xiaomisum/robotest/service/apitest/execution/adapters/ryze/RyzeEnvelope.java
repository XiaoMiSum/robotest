package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import java.util.Map;

/**
 * Ryze 结果信封（GitLab 集成详细设计 4.6 导出文件契约）。
 * <p>
 * 接入方仓库 ryze-testng ≥ 6.1.2 的 {@code RyzeInvokedMethodListener} 将
 * {@code { testClass, testMethod, result }} 以 {@code 类全限定名#方法名.json} 落盘；
 * {@code result} 为 Ryze {@code TestSuiteResult} 树的 fastjson2 序列化（不含类型标识），
 * 物化逻辑见 {@link RyzeEnvelopeParser}。
 *
 * @param result 结果树根节点（已反序列化为 Map，字段名与 Ryze 序列化口径一致）
 */
public record RyzeEnvelope(String testClass, String testMethod, Map<String, Object> result) {

    /** 源侧引用：归属判定键，与批次快照 source_ref 对齐（详设 4.6 归属判定行） */
    public String sourceRef() {
        return testClass + "#" + testMethod;
    }
}
