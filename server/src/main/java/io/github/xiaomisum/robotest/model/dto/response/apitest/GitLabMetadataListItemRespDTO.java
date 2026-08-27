package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;

@Data
public class GitLabMetadataListItemRespDTO {

    private String id;
    private String fullClassName;
    private String displayName;
    private String description;
    private String resourcePath;
    private Boolean isExecutable;
    private List<MethodItem> methods;

    @Data
    public static class MethodItem {
        private String name;
        private String displayName;
        private List<String> annotations;
    }
}
