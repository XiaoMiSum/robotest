package io.github.xiaomisum.robotest.service.apitest.scan;

import java.util.List;

/**
 * 扫描结果：识别出的测试类 + 两类失败分列（SRS BR-1.6 / 详细设计 2.4）。
 *
 * @param classes     含至少一个 @Test + @RyzeTest 成对方法的测试类
 * @param parseFailed 可预期失败（约定违规），不建场景
 * @param errorFailed 真实错误（解析/读取异常），需留痕
 */
public record ScanOutcome(List<ScannedClass> classes, List<ScanIssue> parseFailed, List<ScanIssue> errorFailed) {
}
