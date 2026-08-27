package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_gitlab_test_class_metadata")
public class GitLabTestClassMetadata extends BaseUuidDO<GitLabTestClassMetadata> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID repositoryId;
    private String fullClassName;
    /** 类级注解 JSON 数组 [{name, params}] */
    private String classAnnotations;
    private String displayName;
    private String description;
    private String resourcePath;
    private Boolean isExecutable;
    /** 测试方法清单 JSON 数组 [{name, annotations, displayName}] */
    private String methods;
}
