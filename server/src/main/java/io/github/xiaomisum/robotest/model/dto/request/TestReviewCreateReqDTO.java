package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TestReviewCreateReqDTO {

    @NotBlank(message = "评审标题不能为空")
    private String title;

    private String description;

    @NotEmpty(message = "参与者不能为空")
    private List<String> participantIds;

    @NotEmpty(message = "请选择用例节点")
    private List<SelectedNode> selectedNodes;

    @Data
    public static class SelectedNode {

        private String documentId;

        @NotEmpty(message = "请选择用例")
        private List<String> caseIds;
    }
}
