package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "test_review", autoResultMap = true)
public class TestReview extends BaseUuidDO<TestReview> {

    private String projectId;
    private String title;
    private String description;
    private String initiatorId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<UUID> participantIds;

    private String status;
}
