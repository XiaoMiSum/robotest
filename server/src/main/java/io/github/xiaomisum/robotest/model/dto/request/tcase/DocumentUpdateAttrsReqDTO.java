package io.github.xiaomisum.robotest.model.dto.request.tcase;

import lombok.Data;

import java.util.List;

@Data
public class DocumentUpdateAttrsReqDTO {

    private String id;
    private String title;
    private String type;
    private String priority;
    private Integer sortOrder;
    /** AI 标识移除（置 false）经此帧提交；为空表示不变更 */
    private Boolean aiGenerated;
    /** 标签更新（AI 用例优化）：null 表示不变更，空数组表示清空 */
    private List<String> tags;
    /** 关联需求 id（帧中显式出现才更新，显式 null 表示清空；字段值本身不足以区分省略与清空） */
    private String requirementId;
}
