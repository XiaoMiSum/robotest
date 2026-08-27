package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 公共组件批量操作（启停/删除） */
@Data
public class CommonComponentBatchReqDTO {

    @NotEmpty(message = "ID 列表不能为空")
    private List<UUID> ids;
}
