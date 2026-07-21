package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class TestReviewCreateReqDTO {

    @NotBlank(message = "评审标题不能为空")
    private String title;

    private String description;

    @NotEmpty(message = "参与者不能为空")
    private List<UUID> participantIds;

    @NotEmpty(message = "请选择用例节点")
    private List<SelectedNode> selectedNodes;

    @Data
    public static class SelectedNode {

        private UUID documentId;

        @NotEmpty(message = "请选择用例")
        private List<UUID> caseIds;
    }
}
