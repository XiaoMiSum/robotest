package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_record")
public class TestReviewRecord extends BaseUuidDO<TestReviewRecord> {

    private UUID reviewId;
    private UUID snapshotNodeId;
    private UUID reviewerId;
    private String operationType;
    private String mark;
    private String comment;
}
