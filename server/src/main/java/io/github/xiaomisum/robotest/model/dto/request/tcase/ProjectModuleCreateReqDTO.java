package io.github.xiaomisum.robotest.model.dto.request.tcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ProjectModuleCreateReqDTO {

    private UUID parentId;

    @NotBlank(message = "模块名称不能为空")
    @Size(min = 1, max = 100, message = "模块名称长度为1-100个字符")
    private String name;
}