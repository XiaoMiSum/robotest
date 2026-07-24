package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

import java.util.UUID;

/**
 * 用例列表查询请求 DTO
 */
@Data
public class TestCaseCaseListReqDTO {

    /**
     * 项目 ID（通过 Header 传递）
     */
    private UUID projectId;

    /**
     * 标题关键词（模糊搜索）
     */
    private String keyword;

    /**
     * 优先级筛选
     */
    private String priority;

    /**
     * 页码
     */
    private Integer pageNo = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 20;
}
