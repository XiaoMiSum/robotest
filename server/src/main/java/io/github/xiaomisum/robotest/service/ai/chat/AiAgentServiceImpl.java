package io.github.xiaomisum.robotest.service.ai.chat;

import io.github.xiaomisum.robotest.framework.audit.AuditOperation;
import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAgentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.ai.AiPromptTemplateMapper;
import io.github.xiaomisum.robotest.service.ai.provider.PromptDefaults;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiAgentServiceImpl implements AiAgentService {

    @Resource
    private AiPromptTemplateMapper aiPromptTemplateMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private PromptDefaults promptDefaults;

    @Override
    public List<AiAgentRespDTO> getAgents() {
        Map<String, AiPromptTemplate> customized = aiPromptTemplateMapper.selectList().stream()
                .collect(Collectors.toMap(AiPromptTemplate::getFunctionType, Function.identity(), (a, b) -> a));

        List<AiAgentRespDTO> result = new ArrayList<>();
        for (AiFunctionType type : AiFunctionType.values()) {
            if (!type.hasTemplate()) {
                continue;
            }
            AiAgentRespDTO dto = new AiAgentRespDTO();
            dto.setFunctionType(type.getCode());
            dto.setName(type.getLabel());
            AiPromptTemplate custom = customized.get(type.getCode());
            boolean editable = custom != null && Boolean.TRUE.equals(custom.getFormatEditable());
            // 已自定义 = 格式约束段被解锁编辑过；种子默认锁定（format_editable=false），恢复默认后回到锁定态
            dto.setCustomized(editable);
            dto.setFormatEditable(editable);
            if (custom != null) {
                dto.setUpdatedAt(custom.getUpdatedAt());
                SysUser updater = custom.getUpdatedBy() != null ? sysUserMapper.selectById(custom.getUpdatedBy()) : null;
                dto.setUpdatedBy(updater != null ? updater.getName() : null);
            }
            result.add(dto);
        }
        return result;
    }

    @Override
    public AiAgentDetailRespDTO getAgentDetail(String functionType) {
        AiFunctionType type = requireTemplateFunction(functionType);
        AiPromptTemplate custom = aiPromptTemplateMapper.findByFunctionType(type.getCode());
        if (custom == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_TEMPLATE_NOT_FOUND);
        }

        AiAgentDetailRespDTO dto = new AiAgentDetailRespDTO();
        dto.setFunctionType(type.getCode());
        dto.setName(type.getLabel());
        // 已自定义 = 格式约束段被解锁编辑过（与列表 customized 同源判定）
        dto.setCustomized(Boolean.TRUE.equals(custom.getFormatEditable()));
        dto.setFormatEditable(Boolean.TRUE.equals(custom.getFormatEditable()));
        dto.setRoleInstruction(custom.getRoleInstruction());
        dto.setFormatConstraint(custom.getFormatConstraint());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiPromptTemplate", logParams = false)
    public void saveAgent(String functionType, AiAgentSaveReqDTO reqDTO, UUID userId) {
        AiFunctionType type = requireTemplateFunction(functionType);
        AiPromptTemplate custom = aiPromptTemplateMapper.findByFunctionType(type.getCode());
        if (custom == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_TEMPLATE_NOT_FOUND);
        }

        String effectiveConstraint = custom.getFormatConstraint();
        if (!Boolean.TRUE.equals(reqDTO.getFormatEditable())) {
            // 格式约束段锁定：提交了与生效值不同的内容视为越权修改
            if (reqDTO.getFormatConstraint() != null
                    && !Objects.equals(reqDTO.getFormatConstraint(), effectiveConstraint)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_TEMPLATE_INVALID);
            }
        } else if (reqDTO.getFormatConstraint() != null) {
            effectiveConstraint = reqDTO.getFormatConstraint();
        }

        AiPromptTemplate update = new AiPromptTemplate();
        update.setId(custom.getId());
        update.setRoleInstruction(reqDTO.getRoleInstruction());
        update.setFormatConstraint(effectiveConstraint);
        // 已自定义仅允许 false → true（开启高级开关保存）；置回 false 只能走恢复默认，防止保存角色指令误清标记
        if (Boolean.TRUE.equals(reqDTO.getFormatEditable())) {
            update.setFormatEditable(true);
        }
        update.setUpdatedBy(userId);
        aiPromptTemplateMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiPromptTemplate", logParams = false)
    public void restoreDefault(String functionType, UUID userId) {
        AiFunctionType type = requireTemplateFunction(functionType);
        PromptDefaults.DefaultTemplate defaults = promptDefaults.get(type.getCode());
        if (defaults == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_TEMPLATE_NOT_FOUND);
        }
        AiPromptTemplate custom = aiPromptTemplateMapper.findByFunctionType(type.getCode());
        if (custom == null) {
            // 种子未执行时按默认内容重建，保证数据库始终有记录
            AiPromptTemplate template = new AiPromptTemplate();
            template.setFunctionType(type.getCode());
            template.setRoleInstruction(defaults.roleInstruction());
            template.setFormatConstraint(defaults.formatConstraint());
            // 恢复默认即回到种子锁定态：格式约束段重新锁定，customized 回 false
            template.setFormatEditable(false);
            template.setUpdatedBy(userId);
            aiPromptTemplateMapper.insert(template);
            return;
        }
        AiPromptTemplate update = new AiPromptTemplate();
        update.setId(custom.getId());
        update.setRoleInstruction(defaults.roleInstruction());
        update.setFormatConstraint(defaults.formatConstraint());
        // 恢复默认即回到种子锁定态：格式约束段重新锁定，customized 回 false
        update.setFormatEditable(false);
        update.setUpdatedBy(userId);
        aiPromptTemplateMapper.updateById(update);
    }

    private AiFunctionType requireTemplateFunction(String functionType) {
        AiFunctionType type = AiFunctionType.fromCode(functionType);
        if (type == null || !type.hasTemplate()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        return type;
    }
}
