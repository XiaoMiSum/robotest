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
    /** 成功解析的场景清单：场景级，超出上限截断；闸门拦截时为 null */
    private ParsedScenesDTO parsedScenes;

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
        /** 合并目标环境名（action=merge 时非空） */
        private String target;
        /** 本次并入的 HTTP 配置条数 */
        private Integer httpConfigs;
        /** 本次并入的数据源条数 */
        private Integer dataSources;
    }

    @Data
    public static class DetailDTO {

        /** parseFailed / errorFailed */
        private String type;
        /** 类全限定名#方法名；文件级失败为空 */
        private String sceneKey;
        private String reason;
    }

    @Data
    public static class ParsedScenesDTO {

        /** 成功解析场景总数（与 scenes.create + scenes.update 同口径） */
        private Integer total;
        /** items 超出单次上限被截断 */
        private Boolean truncated;
        private List<ParsedSceneDTO> items;
    }

    @Data
    public static class ParsedSceneDTO {

        /** 类全限定名#方法名 */
        private String sceneKey;
        private String name;
        /** create / update */
        private String action;
    }
}
