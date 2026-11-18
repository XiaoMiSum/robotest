package io.github.xiaomisum.robotest.model.dto.request.review;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class TestReviewUpdateReqDTO {

    @Size(min = 1, max = 200, message = "评审标题长度须在 1-200 字之间")
    private String title;

    private String description;

    @Size(min = 1, message = "参与者不能为空")
    private List<UUID> participantIds;

    private List<UUID> requirementIds;

    /** 仅关联需求集合发生变化时生效 */
    private Boolean replan;
}
