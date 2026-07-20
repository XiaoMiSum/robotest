package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_record")
public class TestReviewRecord extends BaseDO {

    @TableId
    private String id;
    private String reviewId;
    private String snapshotNodeId;
    private String reviewerId;
    private String operationType;
    private String mark;
    private String comment;
}
