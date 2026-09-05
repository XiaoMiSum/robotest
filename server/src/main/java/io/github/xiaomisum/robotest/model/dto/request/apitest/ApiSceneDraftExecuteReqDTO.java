package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景级草稿执行（创建态未保存场景，一键顺序执行全部草稿步骤）。
 * <p>
 * 场景不存在于库中，环境/场景变量/步骤全部由请求体携带，执行结果反映当前
 * 编辑页实时状态。与持久化异步执行不同：无场景 id 可落执行记录与轮询，故同步返回全量结果。
 */
@Data
public class ApiSceneDraftExecuteReqDTO {

    /** 场景名称（仅用于执行上下文标题） */
    private String name;

    /** 缺省使用项目默认环境 */
    private UUID environmentId;

    /** 场景级变量（页面实时，支持 ${} 引用） */
    @Valid
    private List<ApiSceneVariableBatchReqDTO.Variable> sceneVariables;

    /** 待执行的草稿步骤（按数组顺序执行，语义固定为停止运行） */
    @Valid
    private List<DraftStep> steps;

    @Data
    public static class DraftStep {

        private String name;

        /** http/jdbc，V1.2 仅 http 可执行 */
        private String stepType;

        private Boolean enabled;

        /** system/custom/public_step/copy/link */
        private String sourceType;

        /** link 步骤的源定义 id，执行时拉取源最新配置 */
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