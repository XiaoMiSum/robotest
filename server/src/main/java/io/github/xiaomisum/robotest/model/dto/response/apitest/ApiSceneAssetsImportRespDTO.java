package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

/** 全局资产引入响应（测试场景详细设计 3.12） */
@Data
@Builder
public class ApiSceneAssetsImportRespDTO {

    private int imported;

}
