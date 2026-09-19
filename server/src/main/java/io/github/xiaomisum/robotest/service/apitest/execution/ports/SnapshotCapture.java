package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import io.github.xiaomisum.ryze.Result;

import java.util.Map;

/**
 * 结果树 → 持久化 JSON 快照（防腐缝端口，04 §3.1.1）。树形黄金文件（006-full-snapshot-tree）即其字节校验载体。
 * <p>
 * 编排层经由 {@link MappedResult#treeSnapshot()} 取得快照，本端口供适配器内部与黄金测试直接调用。
 */
public interface SnapshotCapture {

    Map<String, Object> capture(Result result);
}