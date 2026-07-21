package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestPlanCreateReqDTO {

    @NotBlank(message = "计划名称不能为空")
    private String name;

    private String description;

    private UUID executorId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String environment;

    @NotEmpty(message = "请选择用例节点")
    private List<SelectedNode> selectedNodes;

    @Data
    public static class SelectedNode {

        private UUID documentId;

        @NotEmpty(message = "请选择用例")
        private List<UUID> caseIds;
    }
}
