package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;

/** 内置函数清单（GET /api/project/functions/builtin）：按分组组织的平台内置函数注册表 */
@Data
public class ApiBuiltinFunctionGroupRespDTO {

    /** 分组名：数据生成 / 日期时间 / 数据处理 / 安全加密 等 */
    private String name;

    private List<BuiltinFunction> functions;

    @Data
    public static class BuiltinFunction {

        /** 调用名（不含 __ 前缀） */
        private String name;

        /** 完整签名示例，如 ${__random(min, max)} */
        private String signature;

        private String description;

        private List<Param> params;

        private String example;

        /** 是否为 Ryze 框架原生内置函数（当前恒为 true，预留自定义函数标记扩展） */
        private Boolean builtin = true;
    }

    @Data
    public static class Param {

        private String name;

        private Boolean required;

        private String description;
    }
}
