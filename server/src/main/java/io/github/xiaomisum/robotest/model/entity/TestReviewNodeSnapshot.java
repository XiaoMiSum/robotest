package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_node_snapshot")
public class TestReviewNodeSnapshot extends BaseUuidDO<TestReviewNodeSnapshot> {

    private UUID reviewId;
    private UUID originalNodeId;
    private UUID documentSnapshotId;
    private UUID parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastMark;
    private UUID lastReviewerId;
    private LocalDateTime lastReviewedAt;
    private Integer sortOrder;
}
