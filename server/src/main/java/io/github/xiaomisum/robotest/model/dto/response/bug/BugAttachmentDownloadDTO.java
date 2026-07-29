package io.github.xiaomisum.robotest.model.dto.response.bug;

import lombok.Data;

@Data
public class BugAttachmentDownloadDTO {

    private String fileName;
    private String contentType;
    private byte[] content;
}
