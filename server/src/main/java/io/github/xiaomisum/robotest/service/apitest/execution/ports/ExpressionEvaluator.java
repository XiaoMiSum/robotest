package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.List;
import java.util.Map;

/**
 * 函数试算表达式求值（防腐缝端口，04 §3.1.1）。
 * 引擎侧求值细节（内置函数注册、函数上下文装配）在适配器层完成，调用方只传入表达式与自定义函数调用器。
 */
public interface ExpressionEvaluator {

    Object evaluate(String expression, Map<String, ScriptCallable> customFunctions);

    /** 自定义函数调用器：参数全量透传 + 上下文变量链 */
    @FunctionalInterface
    interface ScriptCallable {

        Object apply(List<Object> args, Map<String, Object> contextVariables);
    }
}