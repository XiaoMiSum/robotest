package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import io.github.xiaomisum.ryze.Result;

/**
 * 引擎结果树 → 平台结果模型（防腐缝端口，04 §3.1.1）。
 * <p>
 * 编排层只依赖本端口，不 import ryze 类型；签名中的 {@link Result} 仅作为适配器实现壳，
 * 是经评审的例外引用（04 §5 验收「≤2 处豁免」）。
 */
public interface ResultMapper {

    /** 整树投影：root 挂载 {@code treeSnapshot}=整树 JSON 快照（树形黄金文件同源） */
    MappedResult map(Result result);

    String status(Result result);

    String error(Result result);
}