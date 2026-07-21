package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UserBatchStatusReqDTO {

    @NotBlank(message = "用户ID列表不能为空")
    private List<UUID> userIds;

    @NotBlank(message = "状态不能为空")
    private String status;
}
