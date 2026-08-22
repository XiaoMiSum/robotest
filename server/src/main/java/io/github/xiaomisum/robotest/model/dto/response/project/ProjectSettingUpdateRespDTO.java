package io.github.xiaomisum.robotest.model.dto.response.project;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量更新项目设置响应（PUT /api/project/settings），updated 为成功写入的键数量。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSettingUpdateRespDTO {

    private Integer updated;
}
