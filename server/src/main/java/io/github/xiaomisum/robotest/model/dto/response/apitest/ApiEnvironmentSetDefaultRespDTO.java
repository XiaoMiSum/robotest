package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 设置默认环境结果（PATCH /api/project/environments/:id/set-default） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiEnvironmentSetDefaultRespDTO {

    private Boolean success;
}
