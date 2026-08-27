package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 场景设置响应（测试场景详细设计 3.9.1） */
@Data
@Builder
public class ApiSceneSettingsRespDTO {

    private String failureRule;

    private Map<String, Object> cookieConfig;

}
