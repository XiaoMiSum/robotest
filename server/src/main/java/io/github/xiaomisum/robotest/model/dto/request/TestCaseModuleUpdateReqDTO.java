package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestCaseModuleUpdateReqDTO {

    @Size(min = 1, max = 100, message = "模块名称长度为1-100个字符")
    private String name;
}
