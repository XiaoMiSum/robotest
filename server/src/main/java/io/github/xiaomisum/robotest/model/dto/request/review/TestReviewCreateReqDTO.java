package io.github.xiaomisum.robotest.model.dto.request.review;

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

    /** 关联需求；与 selectedNodes 均可为空，均由服务端合并为最终用例集合 */
    private List<UUID> requirementIds;

    private List<SelectedNode> selectedNodes;

    @Data
    public static class SelectedNode {

        private UUID documentId;

        @NotEmpty(message = "请选择用例")
        private List<UUID> caseIds;
    }
}
