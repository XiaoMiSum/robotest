package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class WorkspaceMembersAddReqDTO {

    private List<MemberItem> members;

    @Data
    public static class MemberItem {
        @NotBlank(message = "用户ID不能为空")
        private String userId;
        private String workspaceRole;
    }
}
