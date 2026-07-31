package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConnectivityTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiProviderPresetRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.ProviderPresetRegistry;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai")
public class AiConfigController {

    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private ProviderPresetRegistry providerPresetRegistry;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<AiConfigRespDTO> getConfig() {
        return Result.ok(aiConfigService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<AiConfigRespDTO> saveConfig(@RequestBody @Valid AiConfigSaveReqDTO reqDTO) {
        return Result.ok(aiConfigService.saveConfig(reqDTO));
    }

    @PostMapping("/config/test")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<AiConnectivityTestRespDTO> testConnectivity(@RequestBody @Valid AiConfigTestReqDTO reqDTO) {
        return Result.ok(aiConfigService.testConnectivity(reqDTO));
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<List<AiProviderPresetRespDTO>> getProviders() {
        return Result.ok(providerPresetRegistry.getAll());
    }
}
