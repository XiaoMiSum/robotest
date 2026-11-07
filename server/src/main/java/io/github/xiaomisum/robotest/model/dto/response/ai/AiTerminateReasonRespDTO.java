package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终止原因（总册 2.6.1 terminate_code / terminate_msg 的响应投影，10 位错误码）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTerminateReasonRespDTO {

    private Integer code;

    private String msg;
}
