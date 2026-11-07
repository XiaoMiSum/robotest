package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;

/**
 * 轨迹查询响应（详设 3.6.4）：items 按 seq 升序，nextSeq 为下次请求的 afterSeq 游标。
 */
@Data
public class AiTaskEventsRespDTO {

    private List<AiTaskTraceEventRespDTO> items;

    /** 本页最后一条事件的 seq（无事件时回传入参 afterSeq） */
    private Integer nextSeq;
}
