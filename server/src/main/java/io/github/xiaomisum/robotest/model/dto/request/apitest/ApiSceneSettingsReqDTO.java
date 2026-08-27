package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

/** 更新场景设置（测试场景详细设计 3.9.2） */
@Data
public class ApiSceneSettingsReqDTO {

    /** all / continue，取值非法返回 7210 */
    @Pattern(regexp = "all|continue", message = "失败规则取值非法")
    private String failureRule;

    /** {sharedEnabled, items:[{id, key, value, enabled, domain}]} */
    private Map<String, Object> cookieConfig;

}
