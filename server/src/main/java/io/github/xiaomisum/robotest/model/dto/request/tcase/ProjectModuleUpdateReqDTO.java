package io.github.xiaomisum.robotest.model.dto.request.tcase;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ProjectModuleUpdateReqDTO {

    @Size(min = 1, max = 100, message = "模块名称长度为1-100个字符")
    private String name;

    // targetIndex 非空时视为移动操作：parentId 为目标父目录（空表示根层级），与"不修改父级"区分开
    private UUID parentId;

    @Min(value = 0, message = "目标位置不能为负数")
    private Integer targetIndex;
}