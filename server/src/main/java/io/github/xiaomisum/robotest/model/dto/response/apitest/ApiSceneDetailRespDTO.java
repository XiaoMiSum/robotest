package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 场景详情（测试场景详细设计 3.1.2），含步骤、变量与设置 */
@Data
@Builder
public class ApiSceneDetailRespDTO {

    private UUID id;

    private String name;

    private UUID moduleId;

    private String description;

    private UUID environmentId;

    private String priority;

    /** 状态：draft（草稿）/ published（已发布） */
    private String status;

    /** 当前用户是否已关注该场景 */
    private Boolean followed;

    /** [{name, value, description}] */
    private List<Map<String, Object>> variables;

    /** 场景级处理器 */
    private List<Map<String, Object>> processors;

    /** 乐观锁版本号，保存时回传 */
    private Integer changeVersion;

    private List<Step> steps;

    @Data
    @Builder
    public static class Step {

        private UUID id;

        private String name;

        /** http / jdbc */
        private String stepType;

        private Integer sortOrder;

        private Boolean enabled;

        /** system/custom/public_step/copy/link */
        private String sourceType;

        private UUID sourceId;

        /** 来源接口 ID 与名称（卡片来源 tag） */
        private UUID sourceInterfaceId;

        private String sourceInterfaceName;

        /** 源已删除标记（link 引用降级展示用） */
        private Boolean sourceMissing;

        /** {method, url, headers[], params[], body{type, content}, timeout} */
        private Map<String, Object> requestConfig;

        /** [{id/name/value/source/interfaceVariableId/description/sortOrder}] */
        private List<Map<String, Object>> variables;

        private List<Map<String, Object>> processors;

        /** [{id, name, enabled, target, condition, expected, expression}] */
        private List<Map<String, Object>> validators;

        /** [{id, name, enabled, source, expression, variableName}] */
        private List<Map<String, Object>> extractors;

    }

    @Data
    @Builder
    public static class Variable {

        private String name;

        private String value;

        private String description;

    }

}
