package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "test_case_document_layout", autoResultMap = true)
public class TestCaseDocumentLayout extends BaseUuidDO<TestCaseDocumentLayout> {

    private UUID documentId;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> layoutJson;
}
