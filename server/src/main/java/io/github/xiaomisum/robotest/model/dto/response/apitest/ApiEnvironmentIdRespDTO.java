package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 环境创建/复制结果（POST /api/project/environments、/:id/copy） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiEnvironmentIdRespDTO {

    private String id;
}
