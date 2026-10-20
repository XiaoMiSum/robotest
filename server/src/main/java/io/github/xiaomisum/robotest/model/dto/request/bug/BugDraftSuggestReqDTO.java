package io.github.xiaomisum.robotest.model.dto.request.bug;

import lombok.Data;

/**
 * 新建缺陷实时建议请求（POST /api/project/bugs/analysis/suggest，详设 3.5）。
 */
@Data
public class BugDraftSuggestReqDTO {

    /** 当前输入的缺陷标题，必填（详设 3.5，空或超长 1000018287） */
    private String title;

    /** 已填写的重现步骤，参与上下文但非必填（未改动默认模板时前端不传） */
    private String steps;

    /** 已选择的所属模块，参与上下文但非必填 */
    private String moduleId;
}
