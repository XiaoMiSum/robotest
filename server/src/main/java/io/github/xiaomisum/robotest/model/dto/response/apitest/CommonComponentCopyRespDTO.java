package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 公共组件复制结果（POST /api/project/components/:id/copy） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonComponentCopyRespDTO {

    private String id;
    private String type;
    private String name;
    private String sourceAssetId;
}
