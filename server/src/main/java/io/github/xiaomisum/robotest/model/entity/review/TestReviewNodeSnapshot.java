package io.github.xiaomisum.robotest.model.entity.review;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_node_snapshot")
public class TestReviewNodeSnapshot extends BaseUuidDO<TestReviewNodeSnapshot> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reviewId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID originalNodeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID documentSnapshotId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastMark;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID lastReviewerId;
    private LocalDateTime lastReviewedAt;
    private Integer sortOrder;
    private Boolean aiGenerated;
}
