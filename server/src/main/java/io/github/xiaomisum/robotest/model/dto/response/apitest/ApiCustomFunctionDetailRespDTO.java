package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

@Data
public class ApiCustomFunctionDetailRespDTO {

    private String id;
    private String type;
    private String scope;
    private String name;
    private String description;
    private String paramsDesc;
    private Boolean enabled;
    private String script;
    private String updatedAt;
}
