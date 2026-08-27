package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 复制场景（测试场景详细设计 3.1.6），缺省「原名称（副本）」 */
@Data
public class ApiSceneCopyReqDTO {

    @Size(max = 200)
    private String name;

}
