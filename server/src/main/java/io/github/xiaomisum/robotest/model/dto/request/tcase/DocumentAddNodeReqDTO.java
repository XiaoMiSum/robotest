package io.github.xiaomisum.robotest.model.dto.request.tcase;

import lombok.Data;

@Data
public class DocumentAddNodeReqDTO {

    private String id;
    private String parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
    /** AI 生成标识（挂载执行器写入，缺省 false，协议扩展见智能用例生成详细设计 4.6） */
    private Boolean aiGenerated;
    /** 关联需求 id（仅 case 节点携带；空表示未关联） */
    private String requirementId;
}
