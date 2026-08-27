package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 复制场景/复制步骤的通用 ID+名称响应 */
@Data
@AllArgsConstructor
public class ApiSceneIdNameRespDTO {

    private String id;

    private String name;

}
