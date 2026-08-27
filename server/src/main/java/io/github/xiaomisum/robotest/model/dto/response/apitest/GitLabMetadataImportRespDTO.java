package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;

@Data
public class GitLabMetadataImportRespDTO {

    private Integer classCount;
    private Integer methodCount;
    private Integer executableCount;
    private String commitSha;
    private int addCount;
    private int modifyCount;
    private int removeCount;
}
