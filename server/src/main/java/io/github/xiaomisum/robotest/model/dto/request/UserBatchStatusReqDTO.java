package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserBatchStatusReqDTO {

    @NotBlank(message = "用户ID列表不能为空")
    private List<String> userIds;

    @NotBlank(message = "状态不能为空")
    private String status;
}
