package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GitLabSyncConfigReqDTO {

    private Boolean autoSyncEnabled;

    @Size(max = 500, message = "测试源码路径不能超过 500 字符")
    private String testSourcePath;

    @Size(max = 500, message = "注解过滤条件不能超过 500 字符")
    private String annotationFilter;

    private Boolean onlyWithResourcePath;
}
