package io.github.xiaomisum.robotest.service.apitest.execution;

import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor.ResolvedSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ReportEntryVisitor.StepOutcome;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.common.SceneStepUtil;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景执行编排/映射共享的纯静态工具：无状态、无 Bean 依赖（04 §4.1 步骤 4 拆分收口），
 * 消除 Launcher/Draft/Query/Dataset 间的私有方法重复。
 */
final class SceneExecutionSupport {

    private SceneExecutionSupport() {
    }

    /** 由步骤 map 解析 StepSpec：缺请求配置时返回 errorMessage，编排层据此跳过构建 */
    static ResolvedSpec resolveSpec(Map<String, Object> step) {
        Map<String, Object> config = SceneStepUtil.getMap(step, "requestConfig");
        String name = SceneStepUtil.getString(step, "name", null);
        if (config == null || config.isEmpty()) {
            return new ResolvedSpec(null, "步骤缺少请求配置");
        }
        return new ResolvedSpec(new StepSpec(name,
                config, orEmpty(SceneStepUtil.getList(step, "validators")),
                orEmpty(SceneStepUtil.getList(step, "extractors"))), null);
    }

    /** 草稿步骤（创建态未保存）的 Spec 解析：名称缺省补"步骤"，避免空标题步骤 */
    static ResolvedSpec resolveDraftSpec(String name, Map<String, Object> config,
            List<Map<String, Object>> validators, List<Map<String, Object>> extractors) {
        if (config == null || config.isEmpty()) {
            return new ResolvedSpec(null, "步骤缺少请求配置");
        }
        return new ResolvedSpec(new StepSpec(names(name),
                config, orEmpty(validators), orEmpty(extractors)), null);
    }

    private static String names(String name) {
        return name == null || name.isBlank() ? "步骤" : name;
    }

    /** 未执行步骤的报告中占位条目（enabled=false 或停止运行后续步骤） */
    static Map<String, Object> skippedEntry(Map<String, Object> step) {
        Map<String, Object> entry = new LinkedHashMap<>();
        UUID stepId = SceneStepUtil.getUUID(step, "id");
        entry.put("stepId", stepId != null ? stepId.toString() : null);
        entry.put("name", SceneStepUtil.getString(step, "name", null));
        entry.put("status", "skipped");
        return entry;
    }

    /** 引擎未产出步骤结果时的兜底错误文案（不得把失败静默写成空成功） */
    static String engineFailureMessage(StepOutcome suiteOutcome) {
        String message = suiteOutcome.errorMessage();
        return message == null || message.isBlank() ? "步骤执行异常，引擎未产出结果" : message;
    }

    static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    /** 时间口径：对外一律下发 ISO-8601 UTC 墙钟字符串（docs/00-spec 时间约定），前端按浏览器时区还原 */
    static String toUtcIso(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    static List<Map<String, Object>> orEmpty(List<Map<String, Object>> list) {
        return list == null ? List.of() : list;
    }

    /** 场景投影校验：不存在或不属于项目一律视为场景不存在（跨空间上下文隔离） */
    static ApiScene requireScene(ApiSceneMapper sceneMapper, UUID projectId, UUID sceneId) {
        ApiScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
        return scene;
    }

    static ApiExecutionRecord requireRecord(ApiExecutionRecordMapper mapper, UUID projectId, UUID executionId) {
        ApiExecutionRecord record = mapper.selectById(executionId);
        if (record == null || !record.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_EXECUTION_RECORD_NOT_FOUND);
        }
        return record;
    }
}