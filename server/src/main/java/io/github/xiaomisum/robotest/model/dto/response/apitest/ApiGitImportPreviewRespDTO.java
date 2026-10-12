package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 扫描预览响应（GitLab 集成详细设计 3.2）：不落库，返回将创建 / 更新 / 解析失败的清单与汇总。
 */
@Data
public class ApiGitImportPreviewRespDTO {

    private UUID repoId;
    private String branch;
    private String headCommit;
    /** 幂等闸门判定（详设 3.3；WP-3.2 恒放行，WP-3.3 起真实判定） */
    private GateDTO gate;
    /** 扫描识别的测试类数 */
    private Integer classes;
    /** 有效用例数（@Test 与 @RyzeTest 成对的方法，BR-1.6） */
    private Integer effectiveCases;
    private ModulesDTO modules;
    private ScenesDTO scenes;
    /** 环境合并结果（详设 4.1 批次补齐，当前恒 skip 占位） */
    private EnvironmentDTO environment;
    private List<DetailDTO> details;

    @Data
    public static class GateDTO {

        private Boolean blocked;
        private String reason;
    }

    @Data
    public static class ModulesDTO {

        private CounterDTO containers;
        private ClassesCounterDTO classes;
    }

    @Data
    public static class CounterDTO {

        private Integer create;
        private Integer update;
    }

    @Data
    public static class ClassesCounterDTO {

        private Integer create;
        private Integer update;
        /** 「同包容器 + 同简单名」认领并回填全限定名的类模块数（BR-1.8） */
        private Integer backfill;
    }

    @Data
    public static class ScenesDTO {

        private Integer create;
        private Integer update;
        private Integer parseFailed;
        private Integer errorFailed;
    }

    @Data
    public static class EnvironmentDTO {

        /** merge / skip */
        private String action;
    }

    @Data
    public static class DetailDTO {

        /** parseFailed / errorFailed */
        private String type;
        /** 类全限定名#方法名；文件级失败为空 */
        private String sceneKey;
        private String reason;
    }
}
