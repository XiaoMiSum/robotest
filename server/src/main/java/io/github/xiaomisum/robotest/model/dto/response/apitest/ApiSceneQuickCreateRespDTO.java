package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 通过接口快速创建步骤响应（测试场景详细设计 3.3.2） */
@Data
@Builder
public class ApiSceneQuickCreateRespDTO {

    private List<CreatedStep> steps;

    @Data
    @Builder
    public static class CreatedStep {

        private UUID id;

        private String name;

        /** copy / link */
        private String sourceType;

        private String sourceInterfaceName;

    }

}
