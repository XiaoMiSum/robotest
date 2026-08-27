package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 报告批量操作（批量导出/批量删除，测试报告详细设计 3.2-3.3） */
@Data
public class ApiReportBatchReqDTO {

    @NotEmpty(message = "报告 ID 列表不能为空")
    private List<UUID> ids;

}
