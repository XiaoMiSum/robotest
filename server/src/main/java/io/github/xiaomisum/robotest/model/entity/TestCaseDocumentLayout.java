package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "test_case_document_layout", autoResultMap = true)
public class TestCaseDocumentLayout extends BaseUuidDO<TestCaseDocumentLayout> {

    private String documentId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> layoutJson;
}
