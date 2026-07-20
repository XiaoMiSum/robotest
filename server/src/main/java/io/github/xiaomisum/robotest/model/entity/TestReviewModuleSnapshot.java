package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_review_module_snapshot")
public class TestReviewModuleSnapshot extends BaseDO {

    @TableId
    private String id;
    private String reviewId;
    private String originalModuleId;
    private String parentId;
    private String name;
    private String type;
    private Integer sortOrder;
}
