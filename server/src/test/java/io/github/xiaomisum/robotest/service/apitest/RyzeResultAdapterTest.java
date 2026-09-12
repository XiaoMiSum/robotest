package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.support.ExceptionGroup;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 平台状态/错误消息适配（基础设施详细设计 2.1.4）：broken+断言失败→failed、处理器 skipped、ExceptionGroup 展开 */
class RyzeResultAdapterTest {

    @Test
    void mapStatusLowLevelMapping() {
        assertEquals("success", RyzeResultAdapter.mapStatus(TestStatus.passed));
        assertEquals("failed", RyzeResultAdapter.mapStatus(TestStatus.failed));
        assertEquals("skipped", RyzeResultAdapter.mapStatus(TestStatus.skipped));
        assertEquals("skipped", RyzeResultAdapter.mapStatus(TestStatus.disabled));
        assertEquals("error", RyzeResultAdapter.mapStatus(TestStatus.broken));
        assertEquals("error", RyzeResultAdapter.mapStatus(null));
    }

    @Test
    void brokenSampleWithFailedAssertionResolvesToFailed() {
        DefaultSampleResult sample = new DefaultSampleResult("登录");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new AssertionError("期望 0 实际 1"));
        AssertionResult assertion = new AssertionResult();
        assertion.setStatus(TestStatus.failed);
        sample.addAssertion(assertion);

        assertEquals("failed", RyzeResultAdapter.resolveStepStatus(sample));
    }

    @Test
    void emptyFailedAssertionResolvesToPassed() {
        DefaultSampleResult sample = new DefaultSampleResult("登录");
        sample.setStatus(TestStatus.passed);
        AssertionResult assertion = new AssertionResult();
        assertion.setStatus(TestStatus.failed);
        sample.addAssertion(assertion);

        assertEquals("success", RyzeResultAdapter.resolveStepStatus(sample));
    }

    @Test
    void plainEngineErrorResolvesToError() {
        DefaultSampleResult sample = new DefaultSampleResult("登录");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new RuntimeException("连接被拒绝"));
        assertEquals("error", RyzeResultAdapter.resolveStepStatus(sample));
    }

    @Test
    void exceptionGroupExpandsSubExceptionMessages() {
        DefaultSampleResult sample = new DefaultSampleResult("登录");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new ExceptionGroup("提取器执行失败", List.of(
                new IllegalArgumentException("未提取到数据且无默认值，表达式: $.token"))));

        assertEquals("error", RyzeResultAdapter.resolveStepStatus(sample));
        assertEquals("提取器执行失败：未提取到数据且无默认值，表达式: $.token",
                RyzeResultAdapter.errorMessage(sample));
    }

    @Test
    void exceptionGroupWithMultipleMessagesJoinsDistinct() {
        DefaultSampleResult sample = new DefaultSampleResult("登录");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new ExceptionGroup("提取器执行失败", List.of(
                new IllegalArgumentException("表达式: $.a"),
                new IllegalArgumentException("表达式: $.b"),
                new IllegalArgumentException("表达式: $.b"))));

        assertEquals("提取器执行失败：表达式: $.a；表达式: $.b", RyzeResultAdapter.errorMessage(sample));
    }

    @Test
    void exceptionGroupOfAssertionErrorsResolvesToFailed() {
        DefaultSampleResult sample = new DefaultSampleResult("登录");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new ExceptionGroup("提取器执行失败", List.of(
                new AssertionError("期望 0 实际 1"))));

        assertEquals("failed", RyzeResultAdapter.resolveStepStatus(sample));
    }

    @Test
    void nullResultNullThrowable() {
        assertEquals("error", RyzeResultAdapter.resolveStepStatus(null));
        assertNull(RyzeResultAdapter.errorMessage((io.github.xiaomisum.ryze.Result) null));
        assertNull(RyzeResultAdapter.errorMessage((Throwable) null));
    }
}