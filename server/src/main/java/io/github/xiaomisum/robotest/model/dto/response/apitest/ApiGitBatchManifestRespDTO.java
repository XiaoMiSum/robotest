package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CI 侧取冻结清单响应（GitLab 集成详细设计 4.6）。
 * <p>
 * 幂等：同一 batchId 多次调用返回同一份快照；读的是冻结快照而非平台实时数据，
 * 执行期间用户对场景的增删改不影响正在跑的批次。
 */
@Data
public class ApiGitBatchManifestRespDTO {

    private String batchId;
    /** 默认分支（触发流水线的 ref，快照期取定） */
    private String ref;
    private List<SceneItem> scenes;
    /** 快照期期望用例数总和，供执行结束后对账 */
    private Integer expectedCases;

    /** 单场景条目：CI 侧按 sourceRef（类全限定名#方法名）生成 TestNG 方法过滤条件 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneItem {

        private String sceneId;
        private String sourceRef;
        /** gitlab_ci 场景与测试方法一一对应（BR-1.43），恒为 1 */
        private Integer expectedCaseCount;
    }
}
