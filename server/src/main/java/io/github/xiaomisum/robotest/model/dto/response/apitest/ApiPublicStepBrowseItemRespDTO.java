package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

/**
 * 公共步骤浏览条目
 */
@Data
@Builder
public class ApiPublicStepBrowseItemRespDTO {

    private String id;

    private String name;

    private String method;

    private String path;

    private String interfaceId;

    private String interfaceName;

}
