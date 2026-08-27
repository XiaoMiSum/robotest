package io.github.xiaomisum.robotest.model.dto.request.apitest;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 步骤排序（测试场景详细设计 3.3.6），数组顺序即新排序 */
@Data
public class ApiSceneStepReorderReqDTO {

    private List<UUID> stepIds;

}
