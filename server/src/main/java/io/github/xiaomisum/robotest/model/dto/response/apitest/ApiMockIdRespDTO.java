package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.UUID;

/** Mock 创建/复制/从接口创建的 ID 返回（Mock服务详细设计 3.1.3/3.1.4/3.1.10） */
@Data
public class ApiMockIdRespDTO {

    private UUID id;

    public ApiMockIdRespDTO() {
    }

    public ApiMockIdRespDTO(UUID id) {
        this.id = id;
    }

}
