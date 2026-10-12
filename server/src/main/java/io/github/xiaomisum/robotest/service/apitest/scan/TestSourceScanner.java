package io.github.xiaomisum.robotest.service.apitest.scan;

import java.util.List;

/**
 * 测试工程源码扫描抽象（详细设计 6.3：解析器收敛在内部抽象之后，替换实现不影响上游）。
 * <p>
 * 纯内存组件：调用方负责取回仓库文件内容，本接口只做解析与识别，不落库、不访问网络。
 */
public interface TestSourceScanner {

    /** 输入文件：仓库相对路径 + UTF-8 源码内容 */
    record SourceFile(String path, String content) {
    }

    /**
     * 扫描给定 Java 源文件，识别测试类与有效用例。
     * 块注释剥离为强制前置步骤（位于块注释内的方法一律不计，SRS BR-1.6）。
     */
    ScanOutcome scan(List<SourceFile> files);
}
