package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.Map;

/** Mock 访问地址（Mock服务详细设计 3.1.9） */
@Data
public class ApiMockAddressRespDTO {

    private String mockUrl;
    private String method;
    private Map<String, Object> headers;

}
