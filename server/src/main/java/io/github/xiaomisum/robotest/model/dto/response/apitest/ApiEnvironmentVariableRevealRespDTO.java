package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 敏感变量明文查看（仅维护者）：支撑交互设计的 3 秒临时明文场景 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiEnvironmentVariableRevealRespDTO {

    private String id;

    private String name;

    private String value;
}
