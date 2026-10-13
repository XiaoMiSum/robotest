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

    /** 执行引擎（详设 4.1 派生属性）：platform / gitlab_ci，供执行入口文案与只读形态渲染 */
    private String engine;

    /** 场景来源：platform（平台原生）/ gitlab（GitLab 导入），缺省 platform */
    private String sourceType;

    /** 来源仓库 ID（api_git_repo.id），仅 GitLab 导入场景非空 */
    private UUID repoId;

    /** 源侧稳定键（类全限定名#方法名），导入写入、只读展示 */
    private String sourceId;

    /** 源侧引用（步骤模板或测试方法的相对路径 / 方法签名） */
    private String sourceRef;

    /** 源状态：in_sync/parse_failed/repo_removed/repo_unreachable，仅展示 */
    private String sourceStatus;

    /** 用例信息（GitLab 集成详设 5.3）：仅 gitlab_ci 场景非空，恒单条（场景与测试方法一一对应） */
    private CaseInfo caseInfo;

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
    public static class CaseInfo {

        /** 测试方法名（sourceId `#` 后段） */
        private String methodName;

        /** 用例名称（@Test description，即场景 name） */
        private String caseName;

        /** 源文件相对路径（sourceRef `#` 前段） */
        private String sourceFile;

    }

    @Data
    @Builder
    public static class Variable {

        private String name;

        private String value;

        private String description;

    }

}
