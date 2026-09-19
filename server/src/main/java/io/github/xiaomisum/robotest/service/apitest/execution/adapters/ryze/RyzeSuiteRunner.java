package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.ResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.SuiteRunner;
import io.github.xiaomisum.ryze.Ryze;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 套件执行接缝（端口 {@link SuiteRunner}，04 §3.1.1 切片 3 补充）：{@link Ryze#start} 聚接点收敛于此。
 * 超时/取消/线程池编排留在调用方，本实现保持无状态。
 */
@Component
public class RyzeSuiteRunner implements SuiteRunner {

    private final ResultMapper mapper;

    public RyzeSuiteRunner(ResultMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MappedResult run(Map<String, Object> suite) {
        return mapper.map(Ryze.start(suite));
    }
}