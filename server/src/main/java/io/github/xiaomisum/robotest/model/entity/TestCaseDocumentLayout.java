package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("test_case_document_layout")
public class TestCaseDocumentLayout extends BaseUuidDO<TestCaseDocumentLayout> {

    private String documentId;
    private String layoutJson;
}
