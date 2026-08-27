package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

/** Mock 优先级移动结果（Mock服务详细设计 3.1.12） */
@Data
public class ApiMockMoveRespDTO {

    private Boolean success;

    public ApiMockMoveRespDTO() {
    }

    public ApiMockMoveRespDTO(Boolean success) {
        this.success = success;
    }

}
