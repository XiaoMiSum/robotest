package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.ryze.Result;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.support.ExceptionGroup;
import io.github.xiaomisum.ryze.testelement.sampler.SampleResult;

import java.util.stream.Collectors;

/**
 * Ryze Result → 平台状态/错误消息的唯一适配入口（基础设施详细设计 2.1.4 状态口径）。
 * <p>
 * 适配 ryze 执行异常重构后的语义：采样器/处理器异常统一置 {@code broken}（断言失败除外，
 * 框架在 {@code AbstractSampler}/{@code AbstractProcessor} 内恢复为 {@code failed}）；
 * 多个提取器异常聚合为 {@link ExceptionGroup}；处理器条件不满足置 {@code skipped}。
 * 平台据此把 {@code broken+断言失败}归为 "failed"（报告 partial）、其余引擎异常归 "error"（报告 failed），
 * 并展开 ExceptionGroup 的子异常消息，避免报告只展示泛化的 "提取器执行失败" 引导语。
 * </p>
 */
public final class RyzeResultAdapter {

    private RyzeResultAdapter() {
    }

    /** 低层映射：success/failed/skipped/error（broken 与 null 归 error，断言失败判别见 {@link #resolveStepStatus}） */
    public static String mapStatus(TestStatus status) {
        if (status == TestStatus.passed) {
            return "success";
        }
        if (status == TestStatus.failed) {
            return "failed";
        }
        if (status == TestStatus.skipped || status == TestStatus.disabled) {
            return "skipped";
        }
        return "error";
    }

    /**
     * 步骤级状态：断言失败视为 "failed"，其余非成功状态（broken/引擎异常）为 "error"。
     * <p>
     * 框架已把断言抛出的 {@code AssertionError} 恢复为 {@code failed} 状态，但已发布 jar 未必包含
     * 该修复，这里对 {@code broken} 再做断言失败兼容判别（断言记录失败或 throwable 为 AssertionError）。
     * </p>
     */
    public static String resolveStepStatus(Result result) {
        if (result == null) {
            return "error";
        }
        if (result.getStatus() == TestStatus.broken && isAssertionFailure(result)) {
            return "failed";
        }
        return mapStatus(result.getStatus());
    }

    /** 错误消息：普通异常原样返回；ExceptionGroup 合并子异常消息（提取器失败明细可读化） */
    public static String errorMessage(Result result) {
        return result == null ? null : errorMessage(result.getThrowable());
    }

    /** 错误消息：普通异常原样返回；ExceptionGroup 合并子异常消息 */
    public static String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable instanceof ExceptionGroup group) {
            String joined = group.getExceptions().stream()
                    .map(RyzeResultAdapter::errorMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .distinct()
                    .collect(Collectors.joining("；"));
            return joined.isBlank() ? group.getMessage() : "提取器执行失败：" + joined;
        }
        return throwable.getMessage();
    }

    private static boolean isAssertionFailure(Result result) {
        if (hasFailedAssertion(result)) {
            return true;
        }
        Throwable throwable = result.getThrowable();
        if (throwable instanceof AssertionError) {
            return true;
        }
        return throwable instanceof ExceptionGroup group && group.getExceptions().stream()
                .anyMatch(AssertionError.class::isInstance);
    }

    private static boolean hasFailedAssertion(Result result) {
        if (!(result instanceof SampleResult sample) || sample.getAssertions() == null) {
            return false;
        }
        for (AssertionResult assertion : sample.getAssertions()) {
            if (assertion.getStatus() != null && assertion.getStatus().isFailed()) {
                return true;
            }
        }
        return false;
    }
}