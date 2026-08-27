package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

/** 公共组件列表项（GET /api/project/components）：含当前项目可见的全部作用域记录 */
@Data
public class CommonComponentListItemRespDTO {

    private String id;
    /** project / workspace / global */
    private String scope;
    /** preprocessor / postprocessor / validator / extractor */
    private String type;
    private String name;
    private String description;
    /** 组件配置内容（JSON 字符串） */
    private String config;
    private Boolean enabled;
    private String updatedAt;
}
