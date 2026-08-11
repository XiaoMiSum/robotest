package io.github.xiaomisum.robotest.service.ai.provider;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import io.github.xiaomisum.robotest.repository.ai.AiPromptTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptAssemblerTest {

    @Mock
    private AiPromptTemplateMapper aiPromptTemplateMapper;

    @InjectMocks
    private PromptAssembler promptAssembler;

    @Test
    void estimateTokens_emptyAndNull() {
        assertEquals(0, PromptAssembler.estimateTokens(null));
        assertEquals(0, PromptAssembler.estimateTokens(""));
    }

    @Test
    void estimateTokens_asciiFourCharsPerToken() {
        // 8 个 ASCII 字符 ≈ 2 token
        assertEquals(2, PromptAssembler.estimateTokens("abcdefgh"));
    }

    @Test
    void estimateTokens_chineseOneCharPerToken() {
        // 中文 1 字 ≈ 1 token
        assertEquals(4, PromptAssembler.estimateTokens("测试用例"));
    }

    @Test
    void estimateTokens_mixed() {
        // 4 中文(4) + 4 ASCII(1) = 5
        assertEquals(5, PromptAssembler.estimateTokens("测试用例abcd"));
    }

    @Test
    void estimateTokens_budgetBoundary() {
        // 预算常量存在且为正
        assertTrue(PromptAssembler.INPUT_TOKEN_BUDGET > 0);
    }

    @Test
    void loadSystemPrompt_readsFromDatabase() {
        AiPromptTemplate template = new AiPromptTemplate();
        template.setFunctionType(AiFunctionType.CASE_GENERATION.getCode());
        template.setRoleInstruction("角色指令");
        template.setFormatConstraint("格式约束");
        when(aiPromptTemplateMapper.findByFunctionType(AiFunctionType.CASE_GENERATION.getCode())).thenReturn(template);

        String system = promptAssembler.loadSystemPrompt(AiFunctionType.CASE_GENERATION);

        assertEquals("角色指令\n\n格式约束", system);
    }

    @Test
    void loadSystemPrompt_missingTemplateThrows() {
        // 运行时提示词唯一来源是数据库：未命中视为配置缺失（6013），不做资源文件兜底
        when(aiPromptTemplateMapper.findByFunctionType(AiFunctionType.CASE_GENERATION.getCode())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> promptAssembler.loadSystemPrompt(AiFunctionType.CASE_GENERATION));

        assertEquals(ErrorCodeConstants.AI_PROMPT_TEMPLATE_NOT_FOUND.code(), ex.getCode());
    }
}
