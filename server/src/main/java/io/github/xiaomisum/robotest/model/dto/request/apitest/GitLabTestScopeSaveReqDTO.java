package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class GitLabTestScopeSaveReqDTO {

    @NotNull(message = "仓库 ID 不能为空")
    private UUID repositoryId;

    @NotEmpty(message = "测试范围变量不能为空")
    @Valid
    private List<GitLabTestScopeItem> items;

    @Data
    public static class GitLabTestScopeItem {
        @NotEmpty(message = "变量名不能为空")
        private String variableName;

        @NotEmpty(message = "变量类型不能为空")
        private String scopeType;

        private String description;
    }
}
