package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 变更历史条目（测试场景详细设计 3.11.2），只读追溯 */
@Data
@Builder
public class ApiChangeHistoryItemRespDTO {

    private String id;

    private Integer version;

    private String operatorName;

    /** create / update / import / copy */
    private String changeType;

    /** 变更摘要，如「更新步骤：发送登录请求」 */
    private String changeSummary;

    private Map<String, Object> contentDiff;

    private LocalDateTime changedAt;

}
