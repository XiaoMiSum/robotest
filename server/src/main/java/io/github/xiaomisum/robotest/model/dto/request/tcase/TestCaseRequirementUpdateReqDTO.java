package io.github.xiaomisum.robotest.model.dto.request.tcase;

import lombok.Data;

import java.util.UUID;

/**
 * 用例节点关联需求单值保存请求；requirementId 为 null 表示清空关联。
 */
@Data
public class TestCaseRequirementUpdateReqDTO {

    private UUID requirementId;
}
