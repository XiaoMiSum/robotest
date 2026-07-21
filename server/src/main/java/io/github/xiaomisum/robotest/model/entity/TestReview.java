package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review")
public class TestReview extends BaseUuidDO<TestReview> {

    private String projectId;
    private String title;
    private String description;
    private String initiatorId;
    private String participantIds;
    private String status;
}
