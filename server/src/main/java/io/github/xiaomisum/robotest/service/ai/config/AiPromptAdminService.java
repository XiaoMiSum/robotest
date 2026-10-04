package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPromptSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPromptDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPromptListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPromptVariableDTO;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.ai.AiPromptTemplateMapper;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandlerRegistry;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 场景提示词管理（详设 3.5）：场景以 AiPromptScenes 登记表为准，未登记报 1000018108；
 * 无自定义行时回落处理器内置默认（source = default）。
 */
@Component
public class AiPromptAdminService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)}}");
    private static final int SUMMARY_MAX_LENGTH = 80;

    private static final String OPERATION_SAVE = "save";
    private static final String OPERATION_RESET = "reset";
    private static final String ENTITY_TYPE_AI_PROMPT = "ai_prompt";

    private static final String SOURCE_DEFAULT = "default";
    private static final String SOURCE_CUSTOM = "custom";

    @Resource
    private AiPromptTemplateMapper promptMapper;
    @Resource
    private TaskHandlerRegistry handlerRegistry;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private AuditLogWriter auditLogWriter;
    @Resource
    private ClientIpResolver clientIpResolver;

    /** 列表（3.5，按 scene 升序）：登记表场景与自定义行合并，更新人按用户表回填 */
    public List<AiPromptListItemRespDTO> list() {
        Map<String, AiPromptTemplate> customByScene = promptMapper.selectList(
                        new LambdaQueryWrapperX<AiPromptTemplate>())
                .stream()
                .collect(Collectors.toMap(AiPromptTemplate::getScene, Function.identity(), (a, b) -> a));

        Map<UUID, String> displayNames = resolveDisplayNames(customByScene.values());

        List<AiPromptListItemRespDTO> result = new ArrayList<>();
        for (AiPromptScenes.PromptScene scene : AiPromptScenes.all()) {
            AiPromptListItemRespDTO dto = new AiPromptListItemRespDTO();
            dto.setScene(scene.scene());
            dto.setName(scene.name());
            AiPromptTemplate custom = customByScene.get(scene.scene());
            String content = custom != null ? custom.getContent() : builtinContent(scene.scene());
            dto.setSummary(summarize(content));
            dto.setSource(custom != null ? SOURCE_CUSTOM : SOURCE_DEFAULT);
            dto.setUpdatedByName(custom == null || custom.getUpdatedBy() == null
                    ? null : displayNames.get(custom.getUpdatedBy()));
            dto.setUpdatedAt(custom == null ? null : custom.getUpdatedAt());
            result.add(dto);
        }
        result.sort(java.util.Comparator.comparing(AiPromptListItemRespDTO::getScene));
        return result;
    }

    /** 详情（3.5）：无自定义行时返回内置默认内容，source = default */
    public AiPromptDetailRespDTO get(String scene) {
        AiPromptScenes.PromptScene registered = requireRegistered(scene);
        AiPromptTemplate custom = findByScene(scene);

        AiPromptDetailRespDTO dto = new AiPromptDetailRespDTO();
        dto.setScene(scene);
        dto.setName(registered.name());
        dto.setVariables(toVariableDtos(registered.variables()));
        if (custom != null) {
            dto.setContent(custom.getContent());
            dto.setSource(SOURCE_CUSTOM);
            dto.setVersion(custom.getVersion());
            dto.setUpdatedAt(custom.getUpdatedAt());
        } else {
            dto.setContent(builtinContent(scene));
            dto.setSource(SOURCE_DEFAULT);
            dto.setVersion(0);
            dto.setUpdatedAt(null);
        }
        return dto;
    }

    /** 保存（3.5，创建或覆盖）：变量校验 1000018109；保存后 source = custom、version + 1 */
    @Transactional(rollbackFor = Exception.class)
    public AiPromptDetailRespDTO save(String scene, AiPromptSaveReqDTO req, LoginUser loginUser,
            HttpServletRequest request) {
        AiPromptScenes.PromptScene registered = requireRegistered(scene);
        validateVariables(req.getContent(), registered);

        AiPromptTemplate custom = findByScene(scene);
        if (custom == null) {
            custom = new AiPromptTemplate();
            custom.setScene(scene);
            custom.setName(registered.name());
            custom.setContent(req.getContent());
            custom.setVariables(toVariableMaps(registered.variables()));
            custom.setSource(SOURCE_CUSTOM);
            custom.setVersion(1);
            custom.setUpdatedBy(loginUser.getId());
            promptMapper.insert(custom);
        } else {
            custom.setContent(req.getContent());
            custom.setVersion(custom.getVersion() == null ? 1 : custom.getVersion() + 1);
            custom.setUpdatedBy(loginUser.getId());
            custom.setSource(SOURCE_CUSTOM);
            promptMapper.updateById(custom);
        }

        writeAudit(OPERATION_SAVE, loginUser, request, scene, Map.of("version",
                custom.getVersion() == null ? 1 : custom.getVersion()));
        return get(scene);
    }

    /** 重置（3.5，恢复内置默认）：逻辑删除自定义行，下次保存从 version = 1 重新计 */
    @Transactional(rollbackFor = Exception.class)
    public AiPromptDetailRespDTO reset(String scene, LoginUser loginUser, HttpServletRequest request) {
        requireRegistered(scene);
        AiPromptTemplate custom = findByScene(scene);
        if (custom != null) {
            promptMapper.deleteById(custom.getId());
        }
        writeAudit(OPERATION_RESET, loginUser, request, scene, Map.of());
        return get(scene);
    }

    // ========== 私有 ==========

    private AiPromptScenes.PromptScene requireRegistered(String scene) {
        AiPromptScenes.PromptScene registered = AiPromptScenes.get(scene);
        if (registered == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_SCENE_NOT_FOUND.code(), "场景未登记：" + scene);
        }
        return registered;
    }

    /** {{变量}} ⊆ 登记清单且必填变量齐全，否则 1000018109（3.5） */
    private void validateVariables(String content, AiPromptScenes.PromptScene registered) {
        Set<String> used = extractVariables(content);
        Set<String> known = registered.variables().stream()
                .map(AiPromptScenes.PromptVariable::name)
                .collect(Collectors.toSet());
        for (String variable : used) {
            if (!known.contains(variable)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_INVALID.code(),
                        "未登记的变量 {{" + variable + "}}");
            }
        }
        for (AiPromptScenes.PromptVariable variable : registered.variables()) {
            if (variable.required() && !used.contains(variable.name())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_INVALID.code(),
                        "必填变量缺失 {{" + variable.name() + "}}");
            }
        }
    }

    private static Set<String> extractVariables(String content) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    /** 内置默认：处理器 defaultPrompt（scene 与 type 对应，2.5 注释）；无则空串 */
    private String builtinContent(String scene) {
        TaskHandler handler = handlerRegistry.get(scene);
        String prompt = handler == null ? null : handler.defaultPrompt();
        return prompt == null ? "" : prompt;
    }

    private AiPromptTemplate findByScene(String scene) {
        return promptMapper.selectOne(new LambdaQueryWrapperX<AiPromptTemplate>()
                .eq(AiPromptTemplate::getScene, scene));
    }

    private Map<UUID, String> resolveDisplayNames(java.util.Collection<AiPromptTemplate> rows) {
        Set<UUID> userIds = rows.stream()
                .map(AiPromptTemplate::getUpdatedBy)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, this::displayName, (a, b) -> a));
    }

    private String displayName(SysUser user) {
        return user.getName() == null || user.getName().isBlank() ? user.getUsername() : user.getName();
    }

    private static String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String firstLine = content.strip().lines().findFirst().orElse("").strip();
        return firstLine.length() <= SUMMARY_MAX_LENGTH
                ? firstLine : firstLine.substring(0, SUMMARY_MAX_LENGTH) + "…";
    }

    private static List<AiPromptVariableDTO> toVariableDtos(List<AiPromptScenes.PromptVariable> variables) {
        return variables.stream().map(variable -> {
            AiPromptVariableDTO dto = new AiPromptVariableDTO();
            dto.setName(variable.name());
            dto.setDesc(variable.desc());
            dto.setRequired(variable.required());
            return dto;
        }).toList();
    }

    private static List<Map<String, Object>> toVariableMaps(List<AiPromptScenes.PromptVariable> variables) {
        return variables.stream().map(variable -> {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("name", variable.name());
            map.put("desc", variable.desc());
            map.put("required", variable.required());
            return map;
        }).toList();
    }

    private void writeAudit(String operation, LoginUser loginUser, HttpServletRequest request, String scene,
            Map<String, Object> changes) {
        Map<String, Object> auditChanges = new LinkedHashMap<>(changes);
        auditChanges.put("scene", scene);
        AuditLog record = new AuditLog();
        record.setOperation(operation);
        record.setEntityType(ENTITY_TYPE_AI_PROMPT);
        record.setOperatorId(loginUser.getId());
        record.setOperatorName(loginUser.getUsername());
        record.setRequestIp(clientIpResolver.resolve(request));
        record.setChanges(auditChanges);
        auditLogWriter.write(record);
    }
}
