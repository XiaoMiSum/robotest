package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class TestReviewCasesUpdateReqDTO {

    @NotEmpty(message = "请选择用例节点")
    @Valid
    private List<TestReviewCreateReqDTO.SelectedNode> selectedNodes;
}
