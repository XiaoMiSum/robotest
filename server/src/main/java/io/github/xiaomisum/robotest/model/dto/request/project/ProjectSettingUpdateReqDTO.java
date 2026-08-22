package io.github.xiaomisum.robotest.model.dto.request.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量更新项目设置请求（PUT /api/project/settings）。
 *
 * <p>
 * domain + settingKey 的白名单与 settingValue 格式校验由代码注册表在 Service 层执行，
 * 任一键非法整批拒绝（详细设计《项目设置详细设计说明书》3.3）。
 * </p>
 */
@Data
public class ProjectSettingUpdateReqDTO {

    @NotEmpty(message = "设置项列表不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "业务域不能为空")
        private String domain;

        @NotBlank(message = "设置项标识不能为空")
        private String settingKey;

        @NotBlank(message = "设置值不能为空")
        @Size(max = 500, message = "设置值不能超过 500 字符")
        private String settingValue;
    }
}
