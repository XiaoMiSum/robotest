package io.github.xiaomisum.robotest.service.apitest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaomisum.robotest.framework.mock.MockAccessProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockBatchToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockAddressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockBatchToggleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockDebugRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockMoveRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockDefinition;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiMockDefinitionMapper;
import io.github.xiaomisum.robotest.service.apitest.mock.MockResponseFactory;
import jakarta.annotation.Resource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_INTERFACE_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_MOCK_ADDR_CONFLICT;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_MOCK_NOT_FOUND;
import static xyz.migoo.framework.common.exception.ServiceExceptionUtil.get;

/**
 * Mock 管理实现（Mock服务详细设计 3.1/3.2）
 */
@Service
public class ApiMockServiceImpl implements ApiMockService {

    @Resource
    private ApiMockDefinitionMapper mockMapper;
    @Resource
    private ApiInterfaceMapper interfaceMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private MockAccessProperties mockAccessProperties;
    @Resource
    private Environment environment;

    @Override
    public PageResult<ApiMockItemRespDTO> fetchPage(UUID workspaceId, UUID projectId, UUID userId,
                                                    UUID interfaceId, String search, Boolean enabled,
                                                    PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        PageResult<ApiMockDefinition> page = mockMapper.selectPage(projectId, interfaceId, search, enabled, pageParam);
        List<ApiMockItemRespDTO> items = page.getList().stream().map(this::toItem).toList();
        return new PageResult<>(items, page.getTotal());
    }

    @Override
    public ApiMockDetailRespDTO getDetail(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiMockDefinition definition = getOwned(projectId, id);
        ApiMockDetailRespDTO detail = new ApiMockDetailRespDTO();
        detail.setId(definition.getId());
        detail.setName(definition.getName());
        detail.setInterfaceId(definition.getInterfaceId());
        detail.setInterfaceName(resolveInterfaceName(definition.getInterfaceId()));
        detail.setMethod(definition.getMethod());
        detail.setPath(definition.getPath());
        detail.setPriority(definition.getPriority());
        detail.setDescription(definition.getDescription());
        detail.setMatchRules(definition.getMatchRules());
        detail.setEnabled(definition.getEnabled());
        detail.setFollowApi(definition.getFollowApi());
        detail.setResponseStatus(definition.getResponseStatus());
        detail.setResponseHeaders(definition.getResponseHeaders());
        detail.setResponseBodyType(definition.getResponseBodyType());
        detail.setResponseBody(definition.getResponseBody());
        detail.setDelayMs(definition.getDelayMs());
        detail.setHitCount(definition.getHitCount());
        detail.setLastHitAt(definition.getLastHitAt());
        detail.setGroupSize(mockMapper.selectGroup(projectId, definition.getMethod(), definition.getPath()).size());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMockIdRespDTO create(UUID workspaceId, UUID projectId, UUID userId, ApiMockSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        checkAddressConflict(projectId, null, reqDTO.getMethod(), reqDTO.getPath(), Boolean.TRUE.equals(reqDTO.getEnabled()));
        ApiMockDefinition definition = new ApiMockDefinition();
        applyRequest(definition, projectId, reqDTO);
        if (reqDTO.getPriority() == null) {
            definition.setPriority(mockMapper.selectMaxPriority(projectId, reqDTO.getMethod(), reqDTO.getPath()) + 1);
        }
        mockMapper.insert(definition);
        return new ApiMockIdRespDTO(definition.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMockIdRespDTO createFromInterface(UUID workspaceId, UUID projectId, UUID userId,
                                                UUID interfaceId, ApiMockSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiInterface apiInterface = interfaceMapper.selectById(interfaceId);
        // 继承接口的路径与方法（详细设计 3.1.4），请求体中的同名字段不生效
        if (apiInterface == null || !projectId.equals(apiInterface.getProjectId())) {
            throw get(API_INTERFACE_NOT_FOUND);
        }
        checkAddressConflict(projectId, null, apiInterface.getMethod(), apiInterface.getPath(),
                Boolean.TRUE.equals(reqDTO.getEnabled()));
        ApiMockDefinition definition = new ApiMockDefinition();
        applyRequest(definition, projectId, reqDTO);
        definition.setInterfaceId(interfaceId);
        definition.setMethod(apiInterface.getMethod());
        definition.setPath(apiInterface.getPath());
        if (reqDTO.getPriority() == null) {
            definition.setPriority(
                    mockMapper.selectMaxPriority(projectId, apiInterface.getMethod(), apiInterface.getPath()) + 1);
        }
        mockMapper.insert(definition);
        return new ApiMockIdRespDTO(definition.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiMockSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiMockDefinition existing = getOwned(projectId, id);
        boolean addressChanged = !existing.getMethod().equalsIgnoreCase(reqDTO.getMethod())
                || !existing.getPath().equals(reqDTO.getPath());
        if (addressChanged || Boolean.TRUE.equals(reqDTO.getEnabled())) {
            checkAddressConflict(projectId, id, reqDTO.getMethod(), reqDTO.getPath(),
                    Boolean.TRUE.equals(reqDTO.getEnabled()));
        }
        // C9 部分更新：全量编辑表单语义，仅以请求字段构建载体
        ApiMockDefinition carrier = new ApiMockDefinition();
        carrier.setId(id);
        applyRequest(carrier, projectId, reqDTO);
        mockMapper.updateById(carrier);
    }

    @Override
    public void toggle(UUID workspaceId, UUID projectId, UUID userId, UUID id, boolean enabled) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiMockDefinition existing = getOwned(projectId, id);
        if (enabled) {
            checkAddressConflict(projectId, id, existing.getMethod(), existing.getPath(), true);
        }
        ApiMockDefinition carrier = new ApiMockDefinition();
        carrier.setId(id);
        carrier.setEnabled(enabled);
        mockMapper.updateById(carrier);
    }

    @Override
    public ApiMockBatchToggleRespDTO batchToggle(UUID workspaceId, UUID projectId, UUID userId,
                                                 ApiMockBatchToggleReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        int updated = 0;
        for (UUID id : reqDTO.getIds()) {
            ApiMockDefinition definition = mockMapper.selectById(id);
            // 不存在或跨项目规则跳过并计入失败数（详细设计 3.1.11）
            if (definition == null || !projectId.equals(definition.getProjectId())) {
                continue;
            }
            if (Boolean.TRUE.equals(reqDTO.getEnabled())) {
                boolean conflict = mockMapper.selectGroup(projectId, definition.getMethod(), definition.getPath())
                        .stream()
                        .filter(other -> !id.equals(other.getId()))
                        .anyMatch(other -> Boolean.TRUE.equals(other.getEnabled()));
                if (conflict) {
                    continue;
                }
            }
            // C9 部分更新：仅变更 enabled 字段
            ApiMockDefinition carrier = new ApiMockDefinition();
            carrier.setId(id);
            carrier.setEnabled(reqDTO.getEnabled());
            mockMapper.updateById(carrier);
            updated++;
        }
        return new ApiMockBatchToggleRespDTO(updated);
    }

    @Override
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        getOwned(projectId, id);
        mockMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMockIdRespDTO duplicate(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiMockDefinition source = getOwned(projectId, id);
        ApiMockDefinition copy = new ApiMockDefinition();
        copy.setProjectId(source.getProjectId());
        copy.setInterfaceId(source.getInterfaceId());
        copy.setName(source.getName() + " - 副本");
        copy.setDescription(source.getDescription());
        copy.setMethod(source.getMethod());
        copy.setPath(source.getPath());
        // 默认停用避免与源规则地址冲突；命中统计不随复制（详细设计 3.1.10）
        copy.setEnabled(false);
        copy.setFollowApi(source.getFollowApi());
        copy.setResponseStatus(source.getResponseStatus());
        copy.setResponseHeaders(source.getResponseHeaders() == null ? null : new LinkedHashMap<>(source.getResponseHeaders()));
        copy.setResponseBodyType(source.getResponseBodyType());
        copy.setResponseBody(source.getResponseBody());
        copy.setDelayMs(source.getDelayMs());
        copy.setMatchRules(source.getMatchRules() == null ? null : new ArrayList<>(source.getMatchRules()));
        copy.setHitCount(0L);
        copy.setPriority(mockMapper.selectMaxPriority(projectId, source.getMethod(), source.getPath()) + 1);
        mockMapper.insert(copy);
        return new ApiMockIdRespDTO(copy.getId());
    }

    @Override
    public void resetHitCount(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        getOwned(projectId, id);
        mockMapper.resetHit(id);
    }

    @Override
    public ApiMockAddressRespDTO getAddress(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiMockDefinition definition = getOwned(projectId, id);
        ApiMockAddressRespDTO address = new ApiMockAddressRespDTO();
        address.setMockUrl(buildBaseUrl() + definition.getPath());
        address.setMethod(definition.getMethod());
        address.setHeaders(Map.of());
        return address;
    }

    @Override
    public ApiMockDebugRespDTO debug(UUID workspaceId, UUID projectId, UUID userId, UUID id,
                                     ApiMockDebugReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiMockDefinition definition = getOwned(projectId, id);
        Map<String, Object> example = loadResponseExample(definition.getInterfaceId());
        MockResponseFactory.MockResponse response = MockResponseFactory.build(definition, example);

        long start = System.currentTimeMillis();
        int delay = definition.getDelayMs() == null ? 0 : Math.min(definition.getDelayMs(), 60_000);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        long duration = System.currentTimeMillis() - start;

        ApiMockDebugRespDTO result = new ApiMockDebugRespDTO();
        result.setStatus(response.status());
        result.setHeaders(new LinkedHashMap<>(response.headers()));
        result.setBody(parseBody(response.bodyType(), response.body()));
        result.setDurationMs(duration);
        return result;
    }

    @Override
    public ApiMockMoveRespDTO moveUp(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        return move(workspaceId, projectId, userId, id, -1);
    }

    @Override
    public ApiMockMoveRespDTO moveDown(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        return move(workspaceId, projectId, userId, id, 1);
    }

    private ApiMockMoveRespDTO move(UUID workspaceId, UUID projectId, UUID userId, UUID id, int direction) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiMockDefinition definition = getOwned(projectId, id);
        List<ApiMockDefinition> group = mockMapper.selectGroup(projectId, definition.getMethod(), definition.getPath())
                .stream()
                .sorted(Comparator.comparing(ApiMockDefinition::getPriority,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ApiMockDefinition::getCreatedAt))
                .toList();
        int index = -1;
        for (int i = 0; i < group.size(); i++) {
            if (id.equals(group.get(i).getId())) {
                index = i;
                break;
            }
        }
        int targetIndex = index + direction;
        // 组内边界与单规则组不可移动（详细设计 3.1.12：跨组由前端置灰拦截）
        if (index < 0 || group.size() < 2 || targetIndex < 0 || targetIndex >= group.size()) {
            return new ApiMockMoveRespDTO(false);
        }
        // 与相邻规则交换优先级序号；序号相同时按目标位次重排保证交换生效
        Integer ownPriority = group.get(index).getPriority();
        Integer neighborPriority = group.get(targetIndex).getPriority();
        if (!Objects.equals(ownPriority, neighborPriority)) {
            updatePriority(group.get(index).getId(), neighborPriority);
            updatePriority(group.get(targetIndex).getId(), ownPriority);
        } else {
            int step = direction < 0 ? -1 : 1;
            updatePriority(group.get(index).getId(), neighborPriority + step);
        }
        return new ApiMockMoveRespDTO(true);
    }

    /** C9 部分更新：优先级交换仅写 id + priority */
    private void updatePriority(UUID id, Integer priority) {
        ApiMockDefinition carrier = new ApiMockDefinition();
        carrier.setId(id);
        carrier.setPriority(priority);
        mockMapper.updateById(carrier);
    }

    // ==================== 内部方法 ====================

    private ApiMockDefinition getOwned(UUID projectId, UUID id) {
        ApiMockDefinition definition = mockMapper.selectById(id);
        // 跨项目访问按不存在处理，避免资源探测
        if (definition == null || !projectId.equals(definition.getProjectId())) {
            throw get(API_MOCK_NOT_FOUND);
        }
        return definition;
    }

    /** 同项目同路径同方法的启用冲突校验（错误码 7302） */
    private void checkAddressConflict(UUID projectId, UUID selfId, String method, String path, boolean enabling) {
        if (!enabling) {
            return;
        }
        boolean conflicted = mockMapper.selectGroup(projectId, method, path).stream()
                .filter(other -> !other.getId().equals(selfId))
                .anyMatch(other -> Boolean.TRUE.equals(other.getEnabled()));
        if (conflicted) {
            throw get(API_MOCK_ADDR_CONFLICT);
        }
    }

    private void applyRequest(ApiMockDefinition definition, UUID projectId, ApiMockSaveReqDTO reqDTO) {
        definition.setProjectId(projectId);
        definition.setInterfaceId(reqDTO.getInterfaceId());
        definition.setName(reqDTO.getName());
        definition.setDescription(reqDTO.getDescription());
        definition.setMethod(reqDTO.getMethod());
        definition.setPath(reqDTO.getPath());
        if (reqDTO.getPriority() != null) {
            definition.setPriority(reqDTO.getPriority());
        }
        definition.setMatchRules(reqDTO.getMatchRules());
        definition.setEnabled(reqDTO.getEnabled());
        definition.setFollowApi(Boolean.TRUE.equals(reqDTO.getFollowApi()));
        definition.setResponseStatus(reqDTO.getResponseStatus());
        definition.setResponseHeaders(reqDTO.getResponseHeaders());
        definition.setResponseBodyType(reqDTO.getResponseBodyType() == null ? "json" : reqDTO.getResponseBodyType());
        definition.setResponseBody(reqDTO.getResponseBody());
        definition.setDelayMs(reqDTO.getDelayMs() == null ? 0 : reqDTO.getDelayMs());
    }

    private String resolveInterfaceName(UUID interfaceId) {
        if (interfaceId == null) {
            return null;
        }
        ApiInterface apiInterface = interfaceMapper.selectById(interfaceId);
        return apiInterface == null ? null : apiInterface.getName();
    }

    private Map<String, Object> loadResponseExample(UUID interfaceId) {
        if (interfaceId == null) {
            return null;
        }
        ApiInterface apiInterface = interfaceMapper.selectById(interfaceId);
        return apiInterface == null ? null : apiInterface.getResponseExample();
    }

    private String buildBaseUrl() {
        if (mockAccessProperties.getBaseUrl() != null && !mockAccessProperties.getBaseUrl().isBlank()) {
            return mockAccessProperties.getBaseUrl().replaceAll("/$", "");
        }
        Integer mockPort = mockAccessProperties.getPort();
        int port = mockPort != null
                ? mockPort
                : Integer.parseInt(environment.getProperty("server.port", "8080"));
        return "http://localhost:" + port;
    }

    /** JSON 类型响应体解析为对象返回，解析失败或其余类型回退原始字符串 */
    private Object parseBody(String bodyType, String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        if ("json".equals(bodyType)) {
            try {
                return objectMapper.readValue(body, Object.class);
            } catch (Exception ignored) {
                // 非法 JSON 原样返回便于定位配置问题
            }
        }
        return body;
    }

    private ApiMockItemRespDTO toItem(ApiMockDefinition definition) {
        ApiMockItemRespDTO item = new ApiMockItemRespDTO();
        item.setId(definition.getId());
        item.setName(definition.getName());
        item.setInterfaceId(definition.getInterfaceId());
        item.setMethod(definition.getMethod());
        item.setPath(definition.getPath());
        item.setPriority(definition.getPriority());
        item.setEnabled(definition.getEnabled());
        item.setFollowApi(definition.getFollowApi());
        item.setResponseStatus(definition.getResponseStatus());
        item.setHitCount(definition.getHitCount());
        item.setLastHitAt(definition.getLastHitAt());
        item.setUpdatedAt(definition.getUpdatedAt());
        return item;
    }

}
