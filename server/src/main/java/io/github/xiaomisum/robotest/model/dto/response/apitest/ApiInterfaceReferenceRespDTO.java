package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 引用关系（接口管理详细设计 3.1.7）；场景/Mock 模块上线前恒为空列表 */
@Data
@Builder
public class ApiInterfaceReferenceRespDTO {

    private List<RefItem> scenes;
    private List<RefItem> mocks;

    @Data
    @Builder
    public static class RefItem {
        private UUID id;
        private String name;
    }
}
