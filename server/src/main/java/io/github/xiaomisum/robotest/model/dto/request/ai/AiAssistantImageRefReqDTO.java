package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;

import java.util.UUID;

/**
 * 消息附图引用（详设 3.5 images）：平台级文件资源标识，仅存引用不存字节；
 * 张数、类型与大小由服务层按 1000018264 校验。
 */
@Data
public class AiAssistantImageRefReqDTO {

    /** 文件资源标识（泛化文件模块 file_resource.id） */
    private UUID fileId;
}
