package io.github.xiaomisum.robotest.model.dto.request.requirement;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

import java.util.UUID;

/**
 * 需求列表查询（GET /api/project/requirements，详设 3.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementPageReqDTO extends PageParam {

    /** 状态筛选，逗号分隔多选（draft/confirmed/changed/archived） */
    private String status;

    /** 所属模块筛选，逗号分隔 */
    private String moduleIds;

    private UUID ownerId;

    /** 版本筛选（精确匹配） */
    private String systemVersion;

    /** 覆盖状态筛选：covered/partial/uncovered/pending（联查追溯侧，接口预留） */
    private String coverage;

    /** 关键词：编号前缀/后缀或标题包含（命中规则见详设 4.3） */
    private String keyword;
}
