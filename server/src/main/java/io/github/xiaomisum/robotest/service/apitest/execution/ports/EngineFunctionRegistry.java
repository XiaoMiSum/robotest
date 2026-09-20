package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.Set;

/**
 * 引擎当前实际注册的内置函数名（防腐缝端口，04 §3.1.1）。
 * 实现位于适配器层（读取引擎静态函数注册表），供平台侧以运行时为准枚举可用内置函数。
 */
public interface EngineFunctionRegistry {

    Set<String> functionKeys();
}