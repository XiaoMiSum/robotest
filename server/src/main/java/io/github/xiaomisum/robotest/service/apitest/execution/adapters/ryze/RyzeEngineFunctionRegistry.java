package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.service.apitest.execution.ports.EngineFunctionRegistry;
import io.github.xiaomisum.ryze.function.Function;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerFunctionRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 引擎内置函数清单（端口 {@link EngineFunctionRegistry} 实现）：读取引擎静态函数注册表，保持目录与执行期一致。
 */
@Component
public class RyzeEngineFunctionRegistry implements EngineFunctionRegistry {

    @Override
    public Set<String> functionKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Function function : FreeMarkerFunctionRegistry.getFunctions()) {
            keys.add(function.key());
        }
        return keys;
    }
}