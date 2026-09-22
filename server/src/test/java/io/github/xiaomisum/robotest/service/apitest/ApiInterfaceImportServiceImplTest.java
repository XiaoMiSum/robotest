package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiParsedImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportMapping;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceChangeLog;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportMappingMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceChangeLogMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportSourceFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 接口导入服务：解析导入、URL 导入、预览 */
@ExtendWith(MockitoExtension.class)
class ApiInterfaceImportServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ApiInterfaceMapper interfaceMapper;
    @Mock
    private ApiInterfaceChangeLogMapper changeLogMapper;
    @Mock
    private ApiImportMappingMapper importMappingMapper;
    @Mock
    private ApiImportRecordMapper importRecordMapper;
    @Mock
    private ImportSourceFetcher sourceFetcher;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiInterfaceImportServiceImpl service;

    // ==================== URL 预览 ====================

    @Test
    void previewMarksConflictsByPathAndMethod() {
        String content = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "col", "version": "1.0"},
                  "paths": {
                    "/new":   {"post": {"operationId": "new", "summary": "新建", "responses": {"200": {"description": "ok"}}}},
                    "/exists":{"post": {"operationId": "exists", "summary": "已有", "responses": {"200": {"description": "ok"}}}}
                  }
                }
                """;
        when(sourceFetcher.fetch("https://a.example.com/swagger.json")).thenReturn(content);
        when(interfaceMapper.selectByPathAndMethod(PROJECT_ID, "POST", "/new")).thenReturn(null);
        when(interfaceMapper.selectByPathAndMethod(PROJECT_ID, "POST", "/exists"))
                .thenReturn(existingInterface("已有"));

        ApiImportPreviewRespDTO resp = service.preview(PROJECT_ID, USER_ID, "https://a.example.com/swagger.json", null);

        assertThat(resp.getItems()).extracting(i -> i.getAction())
                .containsExactly("create", "update");
        assertThat(resp.getItems()).extracting(i -> i.getConflict()).containsExactly(false, true);
        assertThat(resp.getSummary()).containsEntry("toCreate", 1).containsEntry("toUpdate", 1);
        verify(importRecordMapper, never()).insert(any(ApiImportRecord.class));
    }

    // ==================== 解析结果导入 ====================

    @Test
    void importParsedCreatesInterfacesMappingsAndRecord() {
        when(importMappingMapper.selectBySource(any(), any(), any())).thenReturn(null);
        doAnswer(invocation -> {
            invocation.getArgument(0, ApiInterface.class).setId(UUID.randomUUID());
            return 1;
        }).when(interfaceMapper).insert(any(ApiInterface.class));
        when(importRecordMapper.insert(any(ApiImportRecord.class))).thenReturn(1);
        when(changeLogMapper.insert(any(ApiInterfaceChangeLog.class))).thenReturn(1);

        ApiParsedImportReqDTO.Operation login = new ApiParsedImportReqDTO.Operation();
        login.setName("登录");
        login.setMethod("POST");
        login.setPath("/auth/login");
        login.setHeaders(List.of(Map.of("key", "Content-Type", "value", "application/json", "enabled", true)));
        login.setBody(Map.of("type", "json", "content", Map.of("username", "admin")));
        ApiParsedImportReqDTO reqDTO = new ApiParsedImportReqDTO();
        reqDTO.setOperations(List.of(login));

        ApiImportResultRespDTO resp = service.importParsed(PROJECT_ID, USER_ID, reqDTO);

        assertThat(resp.getSummary()).containsEntry("created", 1).containsEntry("failed", 0);

        ArgumentCaptor<ApiInterface> interfaceCaptor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper).insert(interfaceCaptor.capture());
        assertThat(interfaceCaptor.getValue().getName()).isEqualTo("登录");
        assertThat(interfaceCaptor.getValue().getProtocol()).isEqualTo("http");
        assertThat(interfaceCaptor.getValue().getStatus()).isEqualTo("enabled");
        assertThat(interfaceCaptor.getValue().getReferenceCount()).isZero();

        ArgumentCaptor<ApiImportMapping> mappingCaptor = ArgumentCaptor.forClass(ApiImportMapping.class);
        verify(importMappingMapper).insert(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getSourceType()).isEqualTo("curl_operation");
        assertThat(mappingCaptor.getValue().getSourceId()).isEqualTo("POST:/auth/login");
        assertThat(mappingCaptor.getValue().getAction()).isEqualTo("created");

        ArgumentCaptor<ApiImportRecord> recordCaptor = ArgumentCaptor.forClass(ApiImportRecord.class);
        verify(importRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo("success");
        assertThat(recordCaptor.getValue().getImportType()).isEqualTo("curl");
        assertThat(recordCaptor.getValue().getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    void importParsedSuffixesDuplicateNames() {
        when(importMappingMapper.selectBySource(any(), any(), any())).thenReturn(null);
        when(interfaceMapper.selectByPathAndMethod(any(), any(), any())).thenReturn(null);
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, null, "登录"))
                .thenReturn(existingInterface("登录"));
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, null, "登录 (2)")).thenReturn(null);
        doAnswer(invocation -> {
            invocation.getArgument(0, ApiInterface.class).setId(UUID.randomUUID());
            return 1;
        }).when(interfaceMapper).insert(any(ApiInterface.class));
        when(importRecordMapper.insert(any(ApiImportRecord.class))).thenReturn(1);
        when(changeLogMapper.insert(any(ApiInterfaceChangeLog.class))).thenReturn(1);

        ApiParsedImportReqDTO.Operation login = new ApiParsedImportReqDTO.Operation();
        login.setName("登录");
        login.setMethod("POST");
        login.setPath("/auth/login");
        ApiParsedImportReqDTO reqDTO = new ApiParsedImportReqDTO();
        reqDTO.setOperations(List.of(login));

        service.importParsed(PROJECT_ID, USER_ID, reqDTO);

        ArgumentCaptor<ApiInterface> captor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("登录 (2)");
    }

    @Test
    void importParsedWithUnknownSourceKeepsPartialRecord() {
        when(importMappingMapper.selectBySource(any(), any(), any())).thenReturn(null);
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_INTERFACE_NOT_FOUND))
                .when(interfaceMapper).insert(any(ApiInterface.class));
        when(importRecordMapper.insert(any(ApiImportRecord.class))).thenReturn(1);

        ApiParsedImportReqDTO.Operation bad = new ApiParsedImportReqDTO.Operation();
        bad.setName("坏条目");
        bad.setMethod("POST");
        bad.setPath("/broken");
        ApiParsedImportReqDTO reqDTO = new ApiParsedImportReqDTO();
        reqDTO.setOperations(List.of(bad));

        ApiImportResultRespDTO resp = service.importParsed(PROJECT_ID, USER_ID, reqDTO);

        assertThat(resp.getErrors()).hasSize(1);
        assertThat(resp.getErrors().get(0)).containsKey("message");
        assertThat(resp.getSummary()).containsEntry("created", 0).containsEntry("failed", 1);
    }

    // ==================== 辅助 ====================

    private ApiInterface existingInterface(String name) {
        ApiInterface entity = new ApiInterface();
        entity.setId(UUID.randomUUID());
        entity.setProjectId(PROJECT_ID);
        entity.setModuleId(null);
        entity.setName(name);
        entity.setProtocol("http");
        entity.setMethod("GET");
        entity.setPath("/users");
        entity.setStatus("enabled");
        entity.setChangeVersion(1);
        entity.setReferenceCount(0);
        return entity;
    }
}
