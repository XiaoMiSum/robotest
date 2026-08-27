package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 公共组件创建/复制结果（POST /api/project/components、/:id/copy） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonComponentIdRespDTO {

    private String id;
}
