package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 导入结果（接口管理详细设计 3.4.1/3.4.2） */
@Data
@Builder
public class ApiImportResultRespDTO {

    private UUID importHistoryId;
    private Map<String, Object> summary;
    private List<Map<String, Object>> errors;
}
