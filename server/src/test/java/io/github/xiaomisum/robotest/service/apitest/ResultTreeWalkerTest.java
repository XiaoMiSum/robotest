package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ResultTreeWalker;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.ResultVisitor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 结果树遍历骨架分派测试（接口测试域重构方案 04 §3.1.2）：
 * suite 递归子树、sample 落叶、非二者其余节点走 visitFailure 三态全测。
 */
class ResultTreeWalkerTest {

    private static final class Recorder implements ResultVisitor {
        private final List<String> events = new ArrayList<>();

        @Override
        public void visitSuite(TestSuiteResult suite) {
            events.add("suite:" + suite.getTitle());
        }

        @Override
        public void visitSample(SampleResult sample) {
            events.add("sample:" + sample.getTitle());
        }

        @Override
        public void visitFailure(Result failed) {
            events.add("failure:" + failed.getTitle());
        }
    }

    @Test
    void suiteBranchRecursesIntoChildrenInOrder() {
        TestSuiteResult root = new TestSuiteResult("根套件");
        TestSuiteResult child = new TestSuiteResult("嵌套套件");
        DefaultSampleResult leaf = new DefaultSampleResult("登录");
        child.addChild(leaf);
        root.addChild(child);

        Recorder recorder = new Recorder();
        ResultTreeWalker.walk(root, recorder);

        assertEquals(List.of("suite:根套件", "suite:嵌套套件", "sample:登录"), recorder.events);
    }

    @Test
    void sampleBranchCallsVisitSampleOnly() {
        DefaultSampleResult leaf = new DefaultSampleResult("登录");

        Recorder recorder = new Recorder();
        ResultTreeWalker.walk(leaf, recorder);

        assertEquals(List.of("sample:登录"), recorder.events);
    }

    @Test
    void failureBranchCallsVisitFailure() {
        Result other = new Result("其他节点") {
        };

        Recorder recorder = new Recorder();
        ResultTreeWalker.walk(other, recorder);

        assertEquals(List.of("failure:其他节点"), recorder.events);
    }

    @Test
    void suiteWithoutChildrenVisitsSuiteOnly() {
        TestSuiteResult empty = new TestSuiteResult("空套件");

        Recorder recorder = new Recorder();
        ResultTreeWalker.walk(empty, recorder);

        assertEquals(List.of("suite:空套件"), recorder.events);
    }
}