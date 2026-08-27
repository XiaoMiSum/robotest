package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;

@Data
public class GitLabFileTreeNodeRespDTO {
    private String name;
    private String path;
    private String type;
    private List<GitLabFileTreeNodeRespDTO> children;
}
