package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;

import java.util.UUID;

/**
 * 缺陷状态变更（State 模式的业务侧载体）。
 * 状态合法性由 BugWorkflow 裁决，本接口承载 resolution/comment 等业务校验与落库。
 */
public interface BugStatusChangeService {

    void changeBugStatus(Bug bug, UUID userId, BugStatusChangeReqDTO reqDTO);
}
