package io.github.xiaomisum.robotest.service.apitest.scan;

/**
 * 扫描识别出的单个有效用例（同时标注 @Test 与 @RyzeTest 的方法，SRS BR-1.6）。
 *
 * @param methodName  方法名
 * @param description @Test 的 description 属性值，gitlab_ci 模式下作为用例标识与名称
 * @param yamlPath    @RyzeTest 指定的步骤模板路径（resourceDir 相对路径）
 */
public record ScannedMethod(String methodName, String description, String yamlPath) {
}
