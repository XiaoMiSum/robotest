package io.github.xiaomisum.robotest.service.apitest.execution.ports;

import java.util.UUID;

/** 环境生效快照端口（04 §3.1.1）：指定环境不可用时回退项目默认环境。 */
public interface EnvironmentSnapshotProvider {

    EnvSnapshot resolve(UUID projectId, UUID environmentId);
}