package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 场景提示词变量（登记常量，交互 2.5 插入与校验依据）。
 */
@Data
public class AiPromptVariableDTO {

    /** 变量名（{{变量名}} 内文本） */
    private String name;

    /** 说明（前端变量清单展示） */
    private String desc;

    /** 保存时 content 是否必须包含该变量 */
    private Boolean required;
}
