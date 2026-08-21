package io.github.xiaomisum.robotest.model.dto.request.tcase;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class TestCaseDocumentUpdateReqDTO {

    @Size(min = 1, max = 100, message = "文档名称长度为1-100个字符")
    private String name;

    // targetIndex 非空时视为移动操作：moduleId 为目标模块（空表示根层级），与"不修改模块"区分开
    private UUID moduleId;

    @Min(value = 0, message = "目标位置不能为负数")
    private Integer targetIndex;

    // 文档布局数据（JSON），非空时整体覆盖
    private Map<String, Object> layout;
}