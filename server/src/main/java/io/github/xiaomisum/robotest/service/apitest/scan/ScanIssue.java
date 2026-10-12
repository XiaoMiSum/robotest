package io.github.xiaomisum.robotest.service.apitest.scan;

/**
 * 扫描失败明细。分两类计数（详细设计 2.4）：
 * <ul>
 *   <li>可预期失败（parseFailed）：约定违规，如 @RyzeTest 值为空、缺 description</li>
 *   <li>真实错误（errorFailed）：语法解析失败、文件读取异常，需留痕排查</li>
 * </ul>
 *
 * @param filePath  仓库相对路径
 * @param methodKey 类全限定名#方法名；文件级失败（如整文件解析失败）时为 null
 * @param reason    可读原因，直接进预览失败明细
 */
public record ScanIssue(String filePath, String methodKey, String reason) {
}
