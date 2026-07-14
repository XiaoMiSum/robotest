package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkspaceCreateReqDTO {

    @NotBlank(message = "名称不能为空")
    @Size(min = 2, max = 50, message = "名称长度为2-50个字符")
    private String name;

    @Size(max = 500, message = "描述最多500个字符")
    private String description;
}
