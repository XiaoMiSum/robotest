package io.github.xiaomisum.robotest.model.entity.review;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_record")
public class TestReviewRecord extends BaseUuidDO<TestReviewRecord> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reviewId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID snapshotNodeId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reviewerId;
    private String operationType;
    private String mark;
    private String comment;
}
