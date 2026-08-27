package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;

@Data
public class GitLabPipelineStatusRespDTO {
    private String pipelineId;
    private String status;
    private Long duration;
    private List<StageItem> stages;

    @Data
    public static class StageItem {
        private String name;
        private String status;
    }
}
