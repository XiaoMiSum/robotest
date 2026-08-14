package io.github.xiaomisum.robotest.service.ai.requirement;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiRequirementSplitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiRequirementSplitRespDTO;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiRequirementSplitAsserts;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * AI 需求文档拆分实现（US-AI-019，详细设计 3.2.3）：
 * 文本截断（超长截断 + warning，不拒绝）→ 网关流式调用 → done 帧结构断言。
 */
@Service
public class AiRequirementSplitServiceImpl implements AiRequirementSplitService {

    private static final String TASK_INSTRUCTION = """
            请将业务数据中的整份需求文档按模块/功能拆分为细粒度需求条目。
            拆分规则：一个需求点 = 一个可测试功能行为（如「用户管理」可拆为新增/编辑/删除/查询用户四条），\
            模块仅作归属分组；条目内容保留原始描述中的关键约束，不得虚构原文没有的功能。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiOutputValidator outputValidator;
    @Resource
    private AiConfigService aiConfigService;

    @Override
    public SseEmitter split(UUID userId, UUID workspaceId, UUID projectId, AiRequirementSplitReqDTO reqDTO) {
        // 长度上限为系统配置项（默认 20000），超限截断 + warning 提示，不拒绝（3.2.3）
        List<String> inputWarnings = new ArrayList<>();
        String text = reqDTO.getText();
        int maxLength = aiConfigService.getIntSetting("importTextMaxLength");
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
            inputWarnings.add("文档超出输入预算，已截断");
        }

        String businessData = "【待拆分文档】\n" + text + '\n';
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, reqDTO.getModelId());
        return aiGatewayService.stream(context, AiFunctionType.REQUIREMENT_SPLIT,
                TASK_INSTRUCTION, businessData, ChatCallOptions.json(), null, splitAssembler(inputWarnings));
    }

    /**
     * done 帧组装：结构化绑定 + 宽容规整（module/title 超长截断计 warnings）+ 结构断言
     */
    private Function<String, Object> splitAssembler(List<String> inputWarnings) {
        return fullContent -> {
            AiRequirementSplitRespDTO.Payload payload = outputValidator.parseAndValidate(
                    fullContent, AiRequirementSplitRespDTO.Payload.class, null);
            List<String> warnings = new ArrayList<>(inputWarnings);
            warnings.addAll(AiRequirementSplitAsserts.normalizeAndAssertModules(payload.getModules()));
            List<AiRequirementSplitRespDTO.Module> modules = payload.getModules() != null ? payload.getModules() : List.of();
            return Map.of("modules", modules, "warnings", warnings);
        };
    }
}
