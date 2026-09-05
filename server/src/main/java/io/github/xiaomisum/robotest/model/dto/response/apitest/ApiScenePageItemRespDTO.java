package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** 场景列表条目（测试场景详细设计 3.1.1） */
@Data
@Builder
public class ApiScenePageItemRespDTO {

    private UUID id;

    private String name;

    private UUID moduleId;

    private UUID environmentId;

    private String priority;

    /** 状态：draft（草稿）/ published（已发布） */
    private String status;

    private Integer stepCount;

    /** 最近一次执行的完成时间，未执行过为空 */
    private LocalDateTime lastExecutedAt;

    /** 最近一次执行状态，success/failed/error/cancelled/timeout/running/pending，未执行过为空 */
    private String lastStatus;

    private LocalDateTime updatedAt;

    /** 当前用户是否已关注该场景 */
    private Boolean followed;

}
