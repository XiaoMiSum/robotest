package io.github.xiaomisum.robotest.model.dto.response.review;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestReviewListRespDTO {

    private UUID id;
    private String title;
    private String status;
    private InitiatorInfo initiator;
    private Integer participantCount;
    /** 参与者名单（去重批量回填），供列表头像堆展示；无参与者时为空数组 */
    private List<ParticipantInfo> participants;
    private LocalDateTime createdAt;
    private long totalAssociated;
    /** 已评审数（总数 − 待评审数），供进度列展示 n/total */
    private long reviewed;
    private long passed;
    private double progressPercent;
    private double passRate;

    @Data
    public static class InitiatorInfo {
        private UUID id;
        private String name;
    }

    @Data
    public static class ParticipantInfo {
        private UUID id;
        private String name;
        private String avatarUrl;
    }
}
