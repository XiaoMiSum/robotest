package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 从全局资产引入（测试场景详细设计 3.12） */
@Data
public class ApiSceneAssetsImportReqDTO {

    @NotBlank
    private String target;

    private UUID stepId;

    @NotNull
    private List<UUID> assetIds;

}
