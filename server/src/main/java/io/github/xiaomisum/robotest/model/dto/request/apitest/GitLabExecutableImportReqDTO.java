package io.github.xiaomisum.robotest.model.dto.request.apitest;

import lombok.Data;

import java.util.List;

@Data
public class GitLabExecutableImportReqDTO {
    private String scope;
    private List<String> classNames;
    private String conflictStrategy;
}
