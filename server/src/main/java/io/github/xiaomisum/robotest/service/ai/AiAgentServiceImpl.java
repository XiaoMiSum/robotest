package io.github.xiaomisum.robotest.service.ai;

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
            dto.setCustomized(custom != null);
            dto.setFormatEditable(custom != null && Boolean.TRUE.equals(custom.getFormatEditable()));
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
        PromptDefaults.DefaultTemplate defaults = promptDefaults.get(type.getCode());
        AiPromptTemplate custom = aiPromptTemplateMapper.findByFunctionType(type.getCode());

        AiAgentDetailRespDTO dto = new AiAgentDetailRespDTO();
        dto.setFunctionType(type.getCode());
        dto.setName(type.getLabel());
        dto.setCustomized(custom != null);
        dto.setFormatEditable(custom != null && Boolean.TRUE.equals(custom.getFormatEditable()));
        dto.setRoleInstruction(custom != null ? custom.getRoleInstruction() : defaults.roleInstruction());
        dto.setFormatConstraint(custom != null ? custom.getFormatConstraint() : defaults.formatConstraint());

        AiAgentDetailRespDTO.Defaults defaultsDto = new AiAgentDetailRespDTO.Defaults();
        defaultsDto.setRoleInstruction(defaults.roleInstruction());
        defaultsDto.setFormatConstraint(defaults.formatConstraint());
        dto.setDefaults(defaultsDto);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "UPDATE", entityType = "AiPromptTemplate", logParams = false)
    public void saveAgent(String functionType, AiAgentSaveReqDTO reqDTO, UUID userId) {
        AiFunctionType type = requireTemplateFunction(functionType);
        PromptDefaults.DefaultTemplate defaults = promptDefaults.get(type.getCode());
        AiPromptTemplate custom = aiPromptTemplateMapper.findByFunctionType(type.getCode());

        String effectiveConstraint = custom != null ? custom.getFormatConstraint() : defaults.formatConstraint();
        if (!Boolean.TRUE.equals(reqDTO.getFormatEditable())) {
            // 格式约束段锁定：提交了与生效值不同的内容视为越权修改
            if (reqDTO.getFormatConstraint() != null
                    && !Objects.equals(reqDTO.getFormatConstraint(), effectiveConstraint)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_PROMPT_TEMPLATE_INVALID);
            }
        } else if (reqDTO.getFormatConstraint() != null) {
            effectiveConstraint = reqDTO.getFormatConstraint();
        }

        if (custom == null) {
            AiPromptTemplate template = new AiPromptTemplate();
            template.setFunctionType(type.getCode());
            template.setRoleInstruction(reqDTO.getRoleInstruction());
            template.setFormatConstraint(effectiveConstraint);
            template.setFormatEditable(reqDTO.getFormatEditable());
            template.setUpdatedBy(userId);
            aiPromptTemplateMapper.insert(template);
        } else {
            AiPromptTemplate update = new AiPromptTemplate();
            update.setId(custom.getId());
            update.setRoleInstruction(reqDTO.getRoleInstruction());
            update.setFormatConstraint(effectiveConstraint);
            update.setFormatEditable(reqDTO.getFormatEditable());
            update.setUpdatedBy(userId);
            aiPromptTemplateMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditOperation(operation = "DELETE", entityType = "AiPromptTemplate", logParams = false)
    public void restoreDefault(String functionType) {
        AiFunctionType type = requireTemplateFunction(functionType);
        AiPromptTemplate custom = aiPromptTemplateMapper.findByFunctionType(type.getCode());
        if (custom != null) {
            aiPromptTemplateMapper.deleteById(custom.getId());
        }
    }

    private AiFunctionType requireTemplateFunction(String functionType) {
        AiFunctionType type = AiFunctionType.fromCode(functionType);
        if (type == null || !type.hasTemplate()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        return type;
    }
}
