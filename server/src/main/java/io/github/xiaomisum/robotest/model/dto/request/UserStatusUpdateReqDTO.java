package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserStatusUpdateReqDTO {

    @NotBlank(message = "状态不能为空")
    private String status;
}
