package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 草稿单步骤调试（创建态未保存场景，使用页面实时数据而非落库）。
 * <p>
 * 与 {@link ApiSceneStepDebugReqDTO} 的区别：场景不存在于库中，场景变量/步骤
 * 全部由请求体携带，让调试结果反映当前编辑页的实时状态（测试场景详细设计 3.6.3）。
 */
@Data
public class ApiSceneStepDraftDebugReqDTO {

    /** 缺省使用项目默认环境 */
    private UUID environmentId;

    /** 场景级变量（页面实时，支持 ${} 引用） */
    @Valid
    private List<ApiSceneVariableBatchReqDTO.Variable> sceneVariables;

    /** 待调试的单步骤（页面实时请求配置/断言/提取器/步骤变量） */
    @Valid
    private Step step;

    @Data
    public static class Step {

        private String name;

        /** http/jdbc，V1.2 仅 http 可执行 */
        private String stepType;

        /** system/custom/public_step/copy/link */
        private String sourceType;

        /** link 步骤的源定义 id，调试时拉取源最新配置 */
        private UUID sourceId;

        /** {method, url, headers[], params[], body{type, content}, timeout} */
        private Map<String, Object> requestConfig;

        /** [{id, name, enabled, target, condition, expected, expression}] */
        private List<Map<String, Object>> validators;

        /** [{id, name, enabled, source, expression, variableName}] */
        private List<Map<String, Object>> extractors;

        /** 步骤级变量（sampler 级），运行时覆盖场景同名变量 */
        private List<ApiSceneStepVariableBatchReqDTO.Variable> stepVariables;

    }

}