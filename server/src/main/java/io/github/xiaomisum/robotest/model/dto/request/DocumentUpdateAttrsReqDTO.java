package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

@Data
public class DocumentUpdateAttrsReqDTO {

    private String id;
    private String title;
    private String type;
    private String priority;
    private Integer sortOrder;
}
