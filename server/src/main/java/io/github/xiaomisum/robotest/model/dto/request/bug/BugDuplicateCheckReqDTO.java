package io.github.xiaomisum.robotest.model.dto.request.bug;

import lombok.Data;

/**
 * 录入时重复检测（POST /api/project/bugs/duplicates/check，详设 3.8）。
 */
@Data
public class BugDuplicateCheckReqDTO {

    /** 新建缺陷标题，必填（详设 3.8，空或超长 1000018284） */
    private String title;

    /** 重现步骤，参与相似检索但非必填 */
    private String steps;

    /** 返回条数，缺省 5 */
    private Integer limit;
}
