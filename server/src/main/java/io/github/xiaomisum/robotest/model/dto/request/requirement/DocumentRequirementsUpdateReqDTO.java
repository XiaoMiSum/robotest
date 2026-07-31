package io.github.xiaomisum.robotest.model.dto.request.requirement;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档关联需求条目全量设置请求（PUT /api/project/documents/{docId}/requirements）。
 */
@Data
public class DocumentRequirementsUpdateReqDTO {

    /** 全量目标关联条目 ID 列表（差量增删由服务端计算） */
    private List<UUID> requirementIds = new ArrayList<>();
}
