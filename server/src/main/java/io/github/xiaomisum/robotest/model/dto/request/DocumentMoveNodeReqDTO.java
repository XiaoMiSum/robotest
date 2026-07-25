package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

@Data
public class DocumentMoveNodeReqDTO {

    private String id;
    private String parentId;
    private Integer sortOrder;
}
