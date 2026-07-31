package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiChatModelEnabledReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiChatModelSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiChatModelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConnectivityTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiProviderPresetRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiSettingsSchemaRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiChatModelService;
import io.github.xiaomisum.robotest.service.ai.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.AiSettingDefinitions;
import io.github.xiaomisum.robotest.service.ai.AiStatisticsService;
import io.github.xiaomisum.robotest.service.ai.AiTaskService;
import io.github.xiaomisum.robotest.service.ai.ProviderPresetRegistry;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/ai")
public class AiConfigController {

    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private AiChatModelService aiChatModelService;
    @Resource
    private AiSettingDefinitions settingDefinitions;
    @Resource
    private ProviderPresetRegistry providerPresetRegistry;
    @Resource
    private AiStatisticsService aiStatisticsService;
    @Resource
    private AiTaskService aiTaskService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<AiConfigRespDTO> getConfig() {
        return Result.ok(aiConfigService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<AiConfigRespDTO> saveConfig(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiConfigSaveReqDTO reqDTO) {
        return Result.ok(aiConfigService.saveConfig(reqDTO, loginUser.getId()));
    }

    @PostMapping("/config/test")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<AiConnectivityTestRespDTO> testConnectivity(@RequestBody @Valid AiConfigTestReqDTO reqDTO) {
        return Result.ok(aiConfigService.testConnectivity(reqDTO));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<AiStatisticsRespDTO> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String groupBy) {
        return Result.ok(aiStatisticsService.getStatistics(startDate, endDate, groupBy));
    }

    @GetMapping("/rebuild-task")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<AiTaskRespDTO> getRebuildTask() {
        return Result.ok(aiTaskService.getLatestRebuildTask());
    }

    @PostMapping("/rebuild-task/retry")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<Void> retryRebuildTask() {
        aiTaskService.retryRebuildTask();
        return Result.ok();
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<List<AiProviderPresetRespDTO>> getProviders() {
        return Result.ok(providerPresetRegistry.getAll());
    }

    // ========== 对话模型管理（3.3.7） ==========

    @GetMapping("/chat-models")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<List<AiChatModelRespDTO>> listChatModels() {
        return Result.ok(aiChatModelService.list());
    }

    @PostMapping("/chat-models")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<AiChatModelRespDTO> createChatModel(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiChatModelSaveReqDTO reqDTO) {
        return Result.ok(aiChatModelService.create(reqDTO, loginUser.getId()));
    }

    @PutMapping("/chat-models/{id}")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<AiChatModelRespDTO> updateChatModel(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid AiChatModelSaveReqDTO reqDTO) {
        return Result.ok(aiChatModelService.update(id, reqDTO, loginUser.getId()));
    }

    @DeleteMapping("/chat-models/{id}")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<Void> deleteChatModel(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        aiChatModelService.delete(id, loginUser.getId());
        return Result.ok();
    }

    @PutMapping("/chat-models/{id}/default")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<Void> setChatModelDefault(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        aiChatModelService.setDefault(id, loginUser.getId());
        return Result.ok();
    }

    @PutMapping("/chat-models/{id}/enabled")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<Void> setChatModelEnabled(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid AiChatModelEnabledReqDTO reqDTO) {
        aiChatModelService.setEnabled(id, reqDTO.getEnabled(), loginUser.getId());
        return Result.ok();
    }

    @GetMapping("/settings-schema")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<List<AiSettingsSchemaRespDTO>> getSettingsSchema() {
        return Result.ok(settingDefinitions.schema());
    }
}
