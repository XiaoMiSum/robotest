package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 场景设置校验与归一化（接口测试域重构方案 04 §4.2）：优先级/状态/同步模式的取值白名单与缺省落值。
 * <p>
 * 纯函数零 IO，仅抛业务异常（C3）。V1.2 场景状态为 draft/published 字段直写（见场景详细设计 3.1.4），
 * 不承载独立发布动作，故不引入发布状态机（00 §4.4 YAGNI 护栏）。
 */
public final class SceneSettingsValidator {

    private SceneSettingsValidator() {
    }

    public static final String SCENE_STATUS_DRAFT = "draft";
    private static final Set<String> SYNC_MODES = Set.of("copy", "link");
    private static final Set<String> SCENE_PRIORITIES = Set.of("P0", "P1", "P2", "P3");
    private static final Set<String> SCENE_STATUSES = Set.of("draft", "published");

    public static void validatePriority(String priority) {
        if (priority != null && !SCENE_PRIORITIES.contains(priority)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                    "优先级仅支持 P0/P1/P2/P3");
        }
    }

    public static void validateStatus(String status) {
        if (status != null && !SCENE_STATUSES.contains(status)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_SETTING_INVALID,
                    "状态仅支持 draft/published");
        }
    }

    /** 缺省落草稿；非法取值已在 validateStatus 拦下 */
    public static String normalizeStatus(String status) {
        return Objects.requireNonNullElse(status, SCENE_STATUS_DRAFT);
    }

    public static String normalizeMode(String mode) {
        String normalized = Objects.requireNonNullElse(mode, "copy");
        if (!SYNC_MODES.contains(normalized)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED, "mode 仅支持 copy/link");
        }
        return normalized;
    }

    /** 创建持久化载体（C9：新建场景携带全量初始字段；步骤/变量经编辑器归一化后随场景整体落库） */
    public static ApiScene buildCreateScene(UUID projectId, ApiSceneCreateReqDTO reqDTO) {
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setProjectId(projectId);
        scene.setName(reqDTO.getName());
        scene.setModuleId(reqDTO.getModuleId());
        scene.setDescription(reqDTO.getDescription());
        scene.setEnvironmentId(reqDTO.getEnvironmentId());
        scene.setPriority(reqDTO.getPriority());
        scene.setStatus(normalizeStatus(reqDTO.getStatus()));
        scene.setProcessors(Objects.requireNonNullElse(reqDTO.getProcessors(), List.of()));
        scene.setChangeVersion(1);
        scene.setSteps(SceneStepEditor.buildSteps(reqDTO.getSteps()));
        scene.setVariables(SceneStepEditor.normalizeVariables(reqDTO.getVariables()));
        return scene;
    }

    /** 更新持久化载体（C9：仅携带 id + 本次改动字段；variables 携带时才全量覆盖） */
    public static ApiScene buildUpdateCarrier(UUID id, ApiSceneUpdateReqDTO reqDTO, int nextVersion) {
        ApiScene carrier = new ApiScene();
        carrier.setId(id);
        carrier.setName(reqDTO.getName());
        carrier.setModuleId(reqDTO.getModuleId());
        carrier.setDescription(reqDTO.getDescription());
        carrier.setEnvironmentId(reqDTO.getEnvironmentId());
        carrier.setPriority(reqDTO.getPriority());
        carrier.setStatus(reqDTO.getStatus());
        carrier.setProcessors(reqDTO.getProcessors());
        carrier.setChangeVersion(nextVersion);
        if (reqDTO.getVariables() != null) {
            carrier.setVariables(SceneStepEditor.normalizeVariables(reqDTO.getVariables()));
        }
        return carrier;
    }
}