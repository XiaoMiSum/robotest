package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

/**
 * 更新场景（测试场景详细设计 3.1.4）；changeVersion 乐观锁，冲突返回 7205
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiSceneUpdateReqDTO extends ApiSceneCreateReqDTO {

    @NotNull
    private Integer changeVersion;

}
