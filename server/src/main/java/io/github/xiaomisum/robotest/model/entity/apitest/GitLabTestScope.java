package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableName;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_gitlab_test_scope", autoResultMap = true)
public class GitLabTestScope extends BaseUuidDO<GitLabTestScope> {
    private UUID repositoryId;
    /** CI 变量名（如 ENVIRONMENT、TEST_SUITE） */
    private String variableName;
    /** 变量类型：env / scope */
    private String scopeType;
    /** 变量说明 */
    private String description;
}
