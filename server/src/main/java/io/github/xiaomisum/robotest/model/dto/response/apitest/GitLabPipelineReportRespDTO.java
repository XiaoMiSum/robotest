package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;
import java.util.Map;

@Data
public class GitLabPipelineReportRespDTO {
    private String reportId;
    private Map<String, Object> summary;
}
