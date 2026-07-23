package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

/**
 * 用例节点属性更新请求 DTO（用于列表视图编辑）
 */
@Data
public class TestCaseNodeUpdateReqDTO {

    /**
     * 节点标题
     */
    private String title;

    /**
     * 优先级
     */
    private String priority;
}
