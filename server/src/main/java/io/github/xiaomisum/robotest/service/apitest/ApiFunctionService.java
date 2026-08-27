package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiCustomFunctionSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiFunctionEvaluateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiCustomFunctionListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiBuiltinFunctionGroupRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiFunctionEvaluateRespDTO;
import java.util.List;
import java.util.UUID;

/**
 * 函数助手服务（基础设施详设 3.8）：内置函数目录、表达式试算、自定义函数 CRUD。
 *
 * <p>维护权限分级：project → api-func:edit、workspace → api-func:edit-space、
 * global → api-func:edit-global；浏览与试算需 api-func:view。</p>
 */
public interface ApiFunctionService {

    List<ApiBuiltinFunctionGroupRespDTO> builtinCatalog();

    /** 对完整调用表达式求值：语法错误 7019，执行失败 7020 */
    ApiFunctionEvaluateRespDTO evaluate(UUID workspaceId, UUID projectId, UUID userId, ApiFunctionEvaluateReqDTO reqDTO);

    List<ApiCustomFunctionListItemRespDTO> fetchCustomList(UUID workspaceId, UUID projectId, UUID userId,
                                                            Boolean enabled, String scope, String keyword);

    ApiCustomFunctionDetailRespDTO fetchCustomDetail(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    ApiCustomFunctionIdRespDTO createCustom(UUID workspaceId, UUID projectId, UUID userId,
                                            ApiCustomFunctionSaveReqDTO reqDTO);

    void updateCustom(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiCustomFunctionSaveReqDTO reqDTO);

    void toggleCustom(UUID workspaceId, UUID projectId, UUID userId, UUID id, boolean enabled);

    void deleteCustom(UUID workspaceId, UUID projectId, UUID userId, UUID id);
}
