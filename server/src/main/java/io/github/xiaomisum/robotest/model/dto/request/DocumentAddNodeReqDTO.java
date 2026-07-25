package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

@Data
public class DocumentAddNodeReqDTO {

    private String id;
    private String parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
}
