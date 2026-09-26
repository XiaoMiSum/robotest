package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.response.admin.AuditLogRespDTO;
import io.github.xiaomisum.robotest.service.admin.audit.AuditQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.migoo.framework.common.exception.GlobalErrorCodeConstants;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 集成测试（QA-009）：分页契约——默认页参、过滤参数绑定与 {@code list/total} 响应信封
 * （规范 01-quality §3：响应、分页；DOC-002 分页口径）。
 *
 * <p>独立 MockMvc 不装配 Spring Security 方法级代理与上下文拦截器，
 * {@code @PreAuthorize} 授权语义由安全链、拦截器与守卫测试覆盖，本测试只锁定分页/过滤契约。
 */
class AuditLogControllerApiTest {

    private MockMvc mockMvc;
    private AuditQueryService auditQueryService;

    @BeforeEach
    void setUp() {
        auditQueryService = mock(AuditQueryService.class);
        AuditLogController controller = new AuditLogController();
        ReflectionTestUtils.setField(controller, "auditQueryService", auditQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private AuditLogRespDTO row(String operatorName, String operation) {
        AuditLogRespDTO dto = new AuditLogRespDTO();
        dto.setOperatorName(operatorName);
        dto.setOperation(operation);
        return dto;
    }

    @Test
    void page_defaultParams_returnsListTotalEnvelope() throws Exception {
        when(auditQueryService.page(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(new PageResult<>(List.of(row("张三", "登录")), 42L));

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.total").value(42))
                .andExpect(jsonPath("$.data.list[0].operatorName").value("张三"))
                .andExpect(jsonPath("$.data.list[0].operation").value("登录"));

        verify(auditQueryService).page(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    void page_filterParams_bindAndForward() throws Exception {
        when(auditQueryService.page(eq("张三"), isNull(), isNull(), any(LocalDate.class), isNull(), eq(2), eq(5)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("operatorName", "张三")
                        .param("beginTime", "2026-09-01")
                        .param("pageNo", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty());

        verify(auditQueryService).page(eq("张三"), isNull(), isNull(),
                eq(LocalDate.of(2026, 9, 1)), isNull(), eq(2), eq(5));
    }
}
