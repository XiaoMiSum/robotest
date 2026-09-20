package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.service.apitest.execution.ports.ExpressionEvaluator;
import io.github.xiaomisum.ryze.context.ContextWrapper;
import io.github.xiaomisum.ryze.context.TestSuiteContext;
import io.github.xiaomisum.ryze.function.Args;
import io.github.xiaomisum.ryze.function.Function;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerFunctionAdapter;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerFunctionRegistry;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerTemplateEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数试算求值（端口 {@link ExpressionEvaluator} 实现）：装配内置 + 自定义函数模型并执行模板表达式。
 * 内置函数取引擎静态注册表；自定义函数经 {@link ScriptFunction} 桥接为引擎函数，与内置函数走同一适配通道。
 */
@Component
public class RyzeExpressionEvaluator implements ExpressionEvaluator {

    @Override
    public Object evaluate(String expression, Map<String, ScriptCallable> customFunctions) {
        // 试算上下文：空套件上下文即可满足内置函数对变量链的访问，无需真实执行环境
        ContextWrapper wrapper = new ContextWrapper(List.of(new TestSuiteContext()));
        Map<String, Object> model = new HashMap<>();
        for (Function function : FreeMarkerFunctionRegistry.getFunctions()) {
            model.put(function.key(), new FreeMarkerFunctionAdapter(wrapper, function));
        }
        customFunctions.forEach((name, callable) -> model.put(name,
                new FreeMarkerFunctionAdapter(wrapper, new ScriptFunction(name, callable))));
        return new FreeMarkerTemplateEngine().evaluate(model, expression);
    }

    /**
     * 自定义函数桥接：试算路径按函数名直接绑定模型，参数不占名称位，全量透传。
     */
    private record ScriptFunction(String name, ScriptCallable callable) implements Function {

        @Override
        public String key() {
            return name;
        }

        @Override
        public Object execute(ContextWrapper contextWrapper, Args args) {
            List<Object> realArgs = (args == null || args.isEmpty()) ? List.of() : new ArrayList<>(args);
            Map<String, Object> contextVars = new LinkedHashMap<>();
            if (contextWrapper != null) {
                contextVars.putAll(contextWrapper.getAllVariablesWrapper().mergeVariables());
            }
            return callable.apply(realArgs, contextVars);
        }
    }
}