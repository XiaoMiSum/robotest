package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.Map;

/**
 * 套件执行接缝（04 §3.1.1 切片 3 补充）：套件 Map → 平台结果模型。
 * <p>
 * {@code Ryze.start} 是引擎耦合点，仅存在于适配器实现内；编排层的超时/线程池/取消编排留在调用方。
 */
public interface SuiteRunner {

    /** 同步执行套件并按结果树投影为平台模型（抛出的异常由调用方决定语义） */
    MappedResult run(Map<String, Object> suite);
}