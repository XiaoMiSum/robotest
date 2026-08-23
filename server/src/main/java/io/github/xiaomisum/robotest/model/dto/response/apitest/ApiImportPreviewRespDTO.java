package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 导入预览（接口管理详细设计 3.4.3）：解析但不入库，展示去重匹配情况 */
@Data
@Builder
public class ApiImportPreviewRespDTO {

    private List<PreviewItem> items;
    private Map<String, Object> summary;

    @Data
    @Builder
    public static class PreviewItem {
        private String name;
        private String method;
        private String path;
        /** create / update / skip */
        private String action;
        /** 与既有接口路径+方法冲突 */
        private Boolean conflict;
    }
}
