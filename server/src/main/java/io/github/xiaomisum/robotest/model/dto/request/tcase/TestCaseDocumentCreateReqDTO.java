package io.github.xiaomisum.robotest.model.dto.request.tcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class TestCaseDocumentCreateReqDTO {

    private UUID moduleId;

    @NotBlank(message = "文档名称不能为空")
    @Size(min = 1, max = 100, message = "文档名称长度为1-100个字符")
    private String name;
}