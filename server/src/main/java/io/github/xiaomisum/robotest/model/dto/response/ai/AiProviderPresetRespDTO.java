package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 供应商预设注册表元数据（代码内置，无落库）
 */
@Data
public class AiProviderPresetRespDTO {

    private String key;
    private String name;
    private List<String> scopes;
    private Map<String, String> defaultBaseUrl;
    private Map<String, List<String>> modelHints;
    private Map<String, List<UniqueParam>> uniqueParams;

    @Data
    public static class UniqueParam {
        /** 支持点号路径表示嵌套参数（如 thinking.type），保存时展开为嵌套对象 */
        private String key;
        /** boolean / number / string / enum */
        private String type;
        private Object defaultValue;
        private List<String> options;
        private String label;
        private String description;
    }
}
