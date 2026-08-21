package io.github.xiaomisum.robotest.model.entity.tcase;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "test_case_document", autoResultMap = true)
public class TestCaseDocument extends BaseUuidDO<TestCaseDocument> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID moduleId;
    private String name;
    private Integer sortOrder;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> layout;
}