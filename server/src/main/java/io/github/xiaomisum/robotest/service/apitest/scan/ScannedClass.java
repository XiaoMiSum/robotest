package io.github.xiaomisum.robotest.service.apitest.scan;

import java.util.List;

/**
 * 扫描识别出的测试类及其有效用例。
 *
 * @param filePath    仓库相对路径（如 src/test/java/com/example/FooTest.java）
 * @param packageName 包名，无包声明时为空串
 * @param className   类简单名
 * @param methods     有效用例，保持源码声明顺序
 */
public record ScannedClass(String filePath, String packageName, String className, List<ScannedMethod> methods) {

    /** 全限定名 = 包名 + 类简单名，是模块与场景的源侧标识（稳定键） */
    public String fullyQualifiedName() {
        return packageName == null || packageName.isBlank() ? className : packageName + "." + className;
    }
}
