package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;

/**
 * 引擎结果树遍历骨架（Template Method，接口测试域重构方案 04 §3.1.2）。
 * 收敛对 {@code TestSuiteResult.children} 的递归，按结果形态分派给 {@link ResultVisitor}；
 * assertions/extractors 是挂载在 SampleResult 上的叶数据，由 visitSample 直接读取，不做节点递归。
 */
public final class ResultTreeWalker {

    private ResultTreeWalker() {
    }

    public static void walk(Result root, ResultVisitor visitor) {
        if (root instanceof TestSuiteResult suite) {
            visitor.visitSuite(suite);
            if (suite.getChildren() != null) {
                for (Result child : suite.getChildren()) {
                    walk(child, visitor);
                }
            }
        } else if (root instanceof SampleResult sample) {
            visitor.visitSample(sample);
        } else {
            visitor.visitFailure(root);
        }
    }
}