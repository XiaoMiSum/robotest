package io.github.xiaomisum.robotest.model.dto.request.tcase;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 保存文档关联需求（PUT /api/project/documents/{docId}/requirements，脑图详设 4.3）：
 * 全量覆盖语义，空数组表示清空关联。
 */
@Data
public class DocumentRequirementsUpdateReqDTO {

    private List<UUID> requirementIds;
}
