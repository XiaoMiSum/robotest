package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量更新接口级变量（接口管理详细设计 3.3.2），全量覆盖：按 name 匹配更新，未包含则删除
 */
@Data
public class ApiInterfaceVariablesReqDTO {

    @Valid
    private List<VariableItem> variables;

    @Data
    public static class VariableItem {

        @NotBlank(message = "变量名不能为空")
        @Size(max = 100, message = "变量名长度不能超过 100")
        private String name;

        private String defaultValue;

        @Size(max = 500, message = "变量描述长度不能超过 500")
        private String description;

        private Boolean required;

        private Integer sortOrder;
    }
}
