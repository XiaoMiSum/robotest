package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.service.apitest.execution.ports.SnapshotCapture;
import io.github.xiaomisum.ryze.Result;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 结果树 → 持久化 JSON 快照（端口 {@link SnapshotCapture} 实现）：委托 {@link RyzeResultSnapshotConverter}。 */
@Component
public class RyzeSnapshotCapture implements SnapshotCapture {

    @Override
    public Map<String, Object> capture(Result result) {
        return RyzeResultSnapshotConverter.toSnapshot(result);
    }
}