package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class GitLabPipelineTriggerReqDTO {

    @NotNull(message = "场景 ID 不能为空")
    private UUID sceneId;

    private Map<String, String> testScope;

    private Map<String, String> variables;
}
