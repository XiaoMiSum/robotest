package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_module_snapshot")
public class TestReviewModuleSnapshot extends BaseUuidDO<TestReviewModuleSnapshot> {

    private UUID reviewId;
    private UUID originalModuleId;
    private UUID parentId;
    private String name;
    private String type;
    private Integer sortOrder;
}
