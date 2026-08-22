package io.github.xiaomisum.robotest.model.dto.response.project;

import lombok.Data;

import java.util.List;

/**
 * 项目设置查询响应（GET /api/project/settings）。
 *
 * <p>
 * 未落库的键一并返回注册表默认值，explicit=false 标识非显式配置。
 * </p>
 */
@Data
public class ProjectSettingListRespDTO {

    private List<Item> items;

    @Data
    public static class Item {

        private String domain;

        private String settingKey;

        /** 生效值：显式配置时为落库值，否则为注册表默认值 */
        private String settingValue;

        /** 注册表默认值（默认值只在代码注册表定义，不落库） */
        private String defaultValue;

        /** 是否显式配置（存在未删除的落库行） */
        private Boolean explicit;
    }
}
