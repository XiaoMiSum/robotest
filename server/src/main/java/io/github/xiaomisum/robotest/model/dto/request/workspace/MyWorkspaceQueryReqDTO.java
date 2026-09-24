package io.github.xiaomisum.robotest.model.dto.request.workspace;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class MyWorkspaceQueryReqDTO extends PageParam {

    @Size(max = 50, message = "关键词长度不能超过50个字符")
    private String keyword;

    private String scope = "all";

    public MyWorkspaceQueryReqDTO() {
        setPageSize(12);
    }
}
