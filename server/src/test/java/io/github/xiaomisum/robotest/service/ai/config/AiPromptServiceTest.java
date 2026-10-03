package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import io.github.xiaomisum.robotest.repository.ai.AiPromptTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPromptServiceTest {

    private static final String SCENE = "requirement_split";

    @Mock
    private AiPromptTemplateMapper promptTemplateMapper;
    @InjectMocks
    private AiPromptService service;

    @Test
    void render_customRowWinsAndReplacesVariables() {
        AiPromptTemplate row = new AiPromptTemplate();
        row.setContent("定制 {{requirementTitle}} 尾");
        when(promptTemplateMapper.selectByScene(SCENE)).thenReturn(row);

        String rendered = service.render(SCENE, "内置", Map.of("requirementTitle", "登录"));

        assertEquals("定制 登录 尾", rendered);
    }

    @Test
    void render_missingVariableKeepsPlaceholder() {
        AiPromptTemplate row = new AiPromptTemplate();
        row.setContent("{{a}}-{{b}}");
        when(promptTemplateMapper.selectByScene(SCENE)).thenReturn(row);

        String rendered = service.render(SCENE, "内置", Map.of("a", "1"));

        assertEquals("1-{{b}}", rendered);
    }

    @Test
    void render_fallsBackToBuiltInDefault() {
        when(promptTemplateMapper.selectByScene(SCENE)).thenReturn(null);

        String rendered = service.render(SCENE, "内置 {{v}}", Map.of("v", "x"));

        assertEquals("内置 x", rendered);
    }

    @Test
    void render_nullVariables_returnsTemplateAsIs() {
        when(promptTemplateMapper.selectByScene(SCENE)).thenReturn(null);

        assertEquals("内置", service.render(SCENE, "内置", null));
    }

    @Test
    void resolve_noRowNoDefault_throwsSceneNotFound() {
        when(promptTemplateMapper.selectByScene(SCENE)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.resolve(SCENE, null));
        assertEquals(ErrorCodeConstants.AI_PROMPT_SCENE_NOT_FOUND.code(), exception.getCode());
    }
}
