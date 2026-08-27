package io.github.xiaomisum.robotest.service.apitest;

import freemarker.core.ParseException;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiCustomFunctionSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiFunctionEvaluateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiBuiltinFunctionGroupRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiFunctionEvaluateRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiFunction;
import io.github.xiaomisum.robotest.repository.apitest.ApiFunctionMapper;
import io.github.xiaomisum.ryze.context.ContextWrapper;
import io.github.xiaomisum.ryze.context.TestSuiteContext;
import io.github.xiaomisum.ryze.function.Args;
import io.github.xiaomisum.ryze.function.Function;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerFunctionRegistry;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerFunctionAdapter;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerTemplateEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiFunctionServiceImpl implements ApiFunctionService {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApiBuiltinFunctionRegistry builtinRegistry;
    private final ApiFunctionScriptEngine scriptEngine;
    private final CustomFunctionRuntime functionRuntime;
    private final ApiFunctionMapper functionMapper;
    private final ProjectAccessGuard projectAccessGuard;

    @Override
    public List<ApiBuiltinFunctionGroupRespDTO> builtinCatalog() {
        return builtinRegistry.catalog();
    }

    @Override
    public ApiFunctionEvaluateRespDTO evaluate(UUID workspaceId, UUID projectId, UUID userId,
                                               ApiFunctionEvaluateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        long start = System.currentTimeMillis();
        // 试算上下文：空套件上下文即可满足内置函数对变量链的访问，无需真实执行环境
        ContextWrapper wrapper = new ContextWrapper(List.of(new TestSuiteContext()));
        Map<String, Object> model = new HashMap<>();
        for (Function function : FreeMarkerFunctionRegistry.getFunctions()) {
            model.put(function.key(), new FreeMarkerFunctionAdapter(wrapper, function));
        }
        functionRuntime.functionsFor(projectId).values()
                .forEach(entry -> model.put(entry.getName(),
                        new FreeMarkerFunctionAdapter(wrapper, new ScriptFunction(entry))));
        Object evaluated;
        try {
            evaluated = new FreeMarkerTemplateEngine().evaluate(model, reqDTO.getExpression());
        } catch (Exception e) {
            // 表达式语法错误与脚本编译错误同码（7019），执行失败走 7020；ryze 会包装底层异常，需沿 cause 链判别
            if (rootCause(e) instanceof ParseException) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_CUSTOM_FUNCTION_SCRIPT_INVALID,
                        rootMessage(e));
            }
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED, rootMessage(e));
        }
        return new ApiFunctionEvaluateRespDTO(String.valueOf(evaluated), System.currentTimeMillis() - start);
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = rootCause(e);
        String message = cause.getMessage();
        return message != null && !message.isBlank() ? message : cause.getClass().getSimpleName();
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    @Override
    public List<ApiCustomFunctionListItemRespDTO> fetchCustomList(UUID workspaceId, UUID projectId, UUID userId,
                                                                    Boolean enabled, String scope, String keyword) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<ApiFunction> list = functionMapper.listVisible(projectId, workspaceId, enabled,
                "custom", scope, keyword);
        return list.stream().map(this::toListItem).toList();
    }

    @Override
    public ApiCustomFunctionDetailRespDTO fetchCustomDetail(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiFunction entity = requireVisible(workspaceId, projectId, id);
        ApiCustomFunctionDetailRespDTO detail = new ApiCustomFunctionDetailRespDTO();
        detail.setId(entity.getId().toString());
        detail.setType(entity.getType());
        detail.setScope(entity.getScope());
        detail.setName(entity.getName());
        detail.setDescription(entity.getDescription());
        detail.setParamsDesc(entity.getParamsDesc());
        detail.setEnabled(entity.getEnabled());
        detail.setScript(entity.getScript());
        detail.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(DATETIME) : null);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiCustomFunctionIdRespDTO createCustom(UUID workspaceId, UUID projectId, UUID userId,
                                                     ApiCustomFunctionSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        String scope = normalizeScope(reqDTO.getScope());
        requireScopeEditPermission(scope);
        assertNameUsable(scope, workspaceId, projectId, reqDTO.getName(), null);
        scriptEngine.assertCompilable(reqDTO.getScript());

        ApiFunction entity = new ApiFunction();
        entity.setType("custom");
        entity.setWorkspaceId("workspace".equals(scope) ? workspaceId : null);
        entity.setProjectId("project".equals(scope) ? projectId : null);
        entity.setScope(scope);
        entity.setName(reqDTO.getName());
        entity.setDescription(reqDTO.getDescription());
        entity.setParamsDesc(reqDTO.getParamsDesc());
        entity.setScript(reqDTO.getScript());
        entity.setEnabled(true);
        entity.setUpdatedBy(userId);
        functionMapper.insert(entity);
        invalidateCache(scope, projectId);
        return new ApiCustomFunctionIdRespDTO(entity.getId().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCustom(UUID workspaceId, UUID projectId, UUID userId, UUID id,
                              ApiCustomFunctionSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiFunction existing = requireVisible(workspaceId, projectId, id);
        requireScopeEditPermission(existing.getScope());
        if (reqDTO.getScope() != null && !existing.getScope().equals(normalizeScope(reqDTO.getScope()))) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_CUSTOM_FUNCTION_NOT_FOUND, "函数作用域创建后不可变更");
        }
        assertNameUsable(existing.getScope(), workspaceId, projectId, reqDTO.getName(), id);
        scriptEngine.assertCompilable(reqDTO.getScript());

        ApiFunction update = new ApiFunction();
        update.setId(id);
        update.setName(reqDTO.getName());
        update.setDescription(reqDTO.getDescription());
        update.setParamsDesc(reqDTO.getParamsDesc());
        update.setScript(reqDTO.getScript());
        update.setUpdatedBy(userId);
        functionMapper.updateById(update);
        invalidateCache(existing.getScope(), projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleCustom(UUID workspaceId, UUID projectId, UUID userId, UUID id, boolean enabled) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiFunction existing = requireVisible(workspaceId, projectId, id);
        requireScopeEditPermission(existing.getScope());

        ApiFunction update = new ApiFunction();
        update.setId(id);
        update.setEnabled(enabled);
        update.setUpdatedBy(userId);
        functionMapper.updateById(update);
        invalidateCache(existing.getScope(), projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustom(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiFunction existing = requireVisible(workspaceId, projectId, id);
        requireScopeEditPermission(existing.getScope());
        functionMapper.deleteById(id);
        invalidateCache(existing.getScope(), projectId);
    }

    private ApiFunction requireVisible(UUID workspaceId, UUID projectId, UUID id) {
        ApiFunction entity = functionMapper.findVisibleById(projectId, workspaceId, id);
        if (entity == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_CUSTOM_FUNCTION_NOT_FOUND);
        }
        return entity;
    }

    /** 函数名校验：不得与内置重名（7018），同作用域唯一（7018） */
    private void assertNameUsable(String scope, UUID workspaceId, UUID projectId, String name, UUID excludeId) {
        if (builtinRegistry.knownKeys().contains(name)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_CUSTOM_FUNCTION_NAME_CONFLICT,
                    "与内置函数重名: " + name);
        }
        if (functionMapper.existsByScopeAndName(scope, workspaceId, projectId, name, excludeId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_CUSTOM_FUNCTION_NAME_CONFLICT,
                    "同作用域下已存在同名函数: " + name);
        }
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "project" : scope;
    }

    private static void requireScopeEditPermission(String scope) {
        String required = switch (scope) {
            case "workspace" -> "api-func:edit-space";
            case "global" -> "api-func:edit-global";
            default -> "api-func:edit";
        };
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = auth != null && auth.getPrincipal() instanceof LoginUser lu ? lu : null;
        if (loginUser == null || !loginUser.getPermissions().contains(required)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    private void invalidateCache(String scope, UUID projectId) {
        // 全局：整体失效；空间：失效该空间下所有已缓存项目；项目：仅失效当前项目
        if ("global".equals(scope)) {
            functionRuntime.invalidateAll();
        } else if ("workspace".equals(scope)) {
            functionRuntime.invalidateByProjectId(projectId);
        } else {
            functionRuntime.invalidate(projectId);
        }
    }

    private ApiCustomFunctionListItemRespDTO toListItem(ApiFunction entity) {
        ApiCustomFunctionListItemRespDTO item = new ApiCustomFunctionListItemRespDTO();
        item.setId(entity.getId().toString());
        item.setType(entity.getType());
        item.setScope(entity.getScope());
        item.setName(entity.getName());
        item.setDescription(entity.getDescription());
        item.setParamsDesc(entity.getParamsDesc());
        item.setEnabled(entity.getEnabled());
        item.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(DATETIME) : null);
        return item;
    }

    /** 函数试算适配：把缓存中的脚本条目包装成 Ryze Function，与内置函数走同一适配通道 */
    record ScriptFunction(CustomFunctionRuntime.ScriptEntry entry) implements Function {

        @Override
        public String key() {
            return entry.getName();
        }

        @Override
        public Object execute(ContextWrapper contextWrapper, Args args) {
            // 试算路径按函数名直接绑定模型，参数不含名称占位符，全量透传
            List<Object> realArgs = (args == null || args.isEmpty()) ? List.of() : new java.util.ArrayList<>(args);
            java.util.Map<String, Object> contextVars = new java.util.LinkedHashMap<>();
            if (contextWrapper != null) {
                contextVars.putAll(contextWrapper.getAllVariablesWrapper().mergeVariables());
            }
            return entry.execute(realArgs, contextVars);
        }
    }
}
