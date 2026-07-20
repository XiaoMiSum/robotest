package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_node_snapshot")
public class TestReviewNodeSnapshot extends BaseDO {

    @TableId
    private String id;
    private String reviewId;
    private String originalNodeId;
    private String documentSnapshotId;
    private String parentId;
    private String title;
    private String type;
    private String priority;
    private Boolean isAssociated;
    private String lastMark;
    private String lastReviewerId;
    private LocalDateTime lastReviewedAt;
    private Integer sortOrder;
}
