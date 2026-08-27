package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 公共组件新建/编辑请求（POST/PUT /api/project/components）
 *
 * <p>scope 决定记录归属与维护权限分级：project → api-component:edit、
 * workspace → api-component:edit-space、global → api-component:edit-global；
 * workspaceId/projectId 由服务端按上下文头与 scope 落库，不接受前端传入。</p>
 */
@Data
public class CommonComponentSaveReqDTO {

    /** preprocessor / postprocessor / validator / extractor，编辑态不可变更 */
    @NotBlank(message = "组件类型不能为空")
    @Pattern(regexp = "preprocessor|postprocessor|validator|extractor", message = "组件类型不合法")
    private String type;

    @NotBlank(message = "组件名称不能为空")
    @Size(max = 100, message = "组件名称不能超过 100 字符")
    private String name;

    @Size(max = 500, message = "组件描述不能超过 500 字符")
    private String description;

    /** project / workspace / global，新建时必传，编辑时传 null 表示不变更 */
    @Pattern(regexp = "project|workspace|global", message = "作用域不合法")
    private String scope;

    /** 组件配置内容（结构与平台内同类型组件一致） */
    private Map<String, Object> config;
}
