package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;

/**
 * 引擎结果树访问者：按结果形态分派（接口测试域重构方案 04 §3.1.2）。
 * 外部 {@code Result} 无 {@code accept()}，由 {@link ResultTreeWalker} 负责遍历分派（Template Method）。
 * assertions/extractors 为 SampleResult 挂载的叶数据，由 visitSample 内读取，不作节点遍历。
 */
public interface ResultVisitor {

    /** TestSuiteResult：可经 suite.getChildren() 递归其子树 */
    void visitSuite(TestSuiteResult suite);

    /** SampleResult：携带 request/response/assertions/extractors，为结果树叶节点 */
    void visitSample(SampleResult sample);

    /** 既非 TestSuiteResult 亦非 SampleResult 的其余 Result（处理器等中间节点） */
    void visitFailure(Result failed);
}