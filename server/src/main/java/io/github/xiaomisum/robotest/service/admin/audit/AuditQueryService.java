package io.github.xiaomisum.robotest.service.admin.audit;

import io.github.xiaomisum.robotest.model.dto.response.admin.AuditLogRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDate;
import java.util.Map;

public interface AuditQueryService {

    PageResult<AuditLogRespDTO> page(String operatorName, String entityType, String operation,
                                     LocalDate beginTime, LocalDate endTime,
                                     Integer pageNo, Integer pageSize);  // operation 为 V1.2 新增可选过滤

    Map<String, Long> aggregate(String entityType, LocalDate from);
}