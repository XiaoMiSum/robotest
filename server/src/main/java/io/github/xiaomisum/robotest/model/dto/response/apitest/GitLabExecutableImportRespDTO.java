package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GitLabExecutableImportRespDTO {
    private String importHistoryId;
    private Map<String, Object> summary;
    private List<SceneItem> scenes;
    private List<Map<String, Object>> errorDetails;

    @Data
    public static class SceneItem {
        private String id;
        private String name;
        private Integer stepCount;
    }
}
