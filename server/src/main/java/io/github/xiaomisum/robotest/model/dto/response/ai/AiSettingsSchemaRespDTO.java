package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;

/**
 * 系统配置项表单定义（代码内置元数据，供管理端渲染分组表单，见详细设计 3.3.8）
 */
@Data
public class AiSettingsSchemaRespDTO {

    /** 分组标识（如 rateLimit） */
    private String group;
    /** 分组显示名（如 限流阈值） */
    private String groupLabel;
    private List<Item> items;

    @Data
    public static class Item {
        /** 配置项键（点分命名，如 rateLimit.generation） */
        private String key;
        /** 控件类型：int / number / object / string[] */
        private String type;
        private String label;
        private String description;
        private Object defaultValue;
        /** 取值范围下限（空表示不限） */
        private Double min;
        /** 取值范围上限（空表示不限） */
        private Double max;
        /** 步进（number 类型可选） */
        private Double step;
        /** 可选项（type=string[] 多选时下发） */
        private List<String> options;
    }
}
