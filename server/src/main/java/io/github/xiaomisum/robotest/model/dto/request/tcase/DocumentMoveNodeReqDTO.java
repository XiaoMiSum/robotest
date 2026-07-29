package io.github.xiaomisum.robotest.model.dto.request.tcase;

import lombok.Data;

@Data
public class DocumentMoveNodeReqDTO {

    private String id;
    private String parentId;
    private Integer sortOrder;
}
