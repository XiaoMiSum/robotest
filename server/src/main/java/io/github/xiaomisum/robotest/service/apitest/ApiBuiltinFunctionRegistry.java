package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.ryze.function.Function;
import io.github.xiaomisum.ryze.template.freemarker.FreeMarkerFunctionRegistry;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiBuiltinFunctionGroupRespDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置函数注册表：以 Ryze 运行时实际注册的函数为准（{@link FreeMarkerFunctionRegistry}），
 * 叠加平台侧维护的分组与描述元数据，保证「函数助手」展示清单与执行期可用函数一致。
 *
 * <p>为什么以运行时为基准而非纯静态清单：Ryze 升级新增/移除内置函数时无需同步改代码，
 * 未匹配到元数据的函数回退通用描述，避免出现「文档有、执行无」的漂移。</p>
 */
@Component
public class ApiBuiltinFunctionRegistry {

    /** 函数名 → 元数据（key 为不含 __ 前缀的调用名，与 ryze Function#key() 对齐） */
    private static final Map<String, Meta> METADATA = buildMetadata();

    private record Meta(String group, String description, String signature,
                        List<ApiBuiltinFunctionGroupRespDTO.Param> params, String example) {
    }

    /** 平台已知全部内置调用名（含元数据缺失项），用于自定义函数重名校验 */
    public Set<String> knownKeys() {
        Set<String> keys = new LinkedHashSet<>(METADATA.keySet());
        for (Function function : FreeMarkerFunctionRegistry.getFunctions()) {
            keys.add(function.key());
        }
        return keys;
    }

    /** 分组目录：仅包含当前运行时真实可调用的函数 */
    public List<ApiBuiltinFunctionGroupRespDTO> catalog() {
        Map<String, List<ApiBuiltinFunctionGroupRespDTO.BuiltinFunction>> grouped = new LinkedHashMap<>();
        for (Function function : FreeMarkerFunctionRegistry.getFunctions()) {
            String key = function.key();
            Meta meta = METADATA.get(key);
            ApiBuiltinFunctionGroupRespDTO.BuiltinFunction item = new ApiBuiltinFunctionGroupRespDTO.BuiltinFunction();
            item.setName(key);
            item.setSignature(meta != null ? meta.signature() : "${" + key + "(...)}");
            item.setDescription(meta != null ? meta.description() : "Ryze 内置函数");
            item.setParams(meta != null ? meta.params() : List.of());
            item.setExample(meta != null ? meta.example() : "${" + key + "()}");
            item.setBuiltin(true);
            grouped.computeIfAbsent(meta != null ? meta.group() : "其他", k -> new ArrayList<>()).add(item);
        }
        List<ApiBuiltinFunctionGroupRespDTO> result = new ArrayList<>();
        grouped.forEach((group, functions) -> {
            ApiBuiltinFunctionGroupRespDTO dto = new ApiBuiltinFunctionGroupRespDTO();
            dto.setName(group);
            dto.setFunctions(functions);
            result.add(dto);
        });
        return result;
    }

    private static Meta meta(String group, String description, String signature, String example,
                             ApiBuiltinFunctionGroupRespDTO.Param... params) {
        return new Meta(group, description, signature, List.of(params), example);
    }

    private static ApiBuiltinFunctionGroupRespDTO.Param param(String name, boolean required, String description) {
        ApiBuiltinFunctionGroupRespDTO.Param p = new ApiBuiltinFunctionGroupRespDTO.Param();
        p.setName(name);
        p.setRequired(required);
        p.setDescription(description);
        return p;
    }

    private static Map<String, Meta> buildMetadata() {
        Map<String, Meta> map = new LinkedHashMap<>();
        map.put("random", meta("数据生成", "生成指定区间的随机整数", "${random(min, max)}", "${random(1, 100)}",
                param("min", true, "区间下界"), param("max", true, "区间上界")));
        map.put("random_string", meta("数据生成", "生成指定长度的随机字符串（含字母与数字）", "${random_string(length)}", "${random_string(8)}",
                param("length", true, "字符串长度")));
        map.put("faker", meta("数据生成", "按 Faker 表达式生成仿真测试数据", "${faker(path[, locale])}", "${faker(name.fullName, zh_CN)}",
                param("path", true, "Faker 数据路径，如 name.fullName"),
                param("locale", false, "语言区域，默认 zh_CN")));
        map.put("uuid", meta("数据生成", "生成随机 UUID（去连字符）", "${uuid()}", "${uuid()}"));
        map.put("timestamp", meta("日期时间", "当前时间戳，默认毫秒；可指定秒或日期格式", "${timestamp([format|_s])}", "${timestamp(_s)}",
                param("format|_s", false, "日期格式串；传 _s 返回秒级时间戳")));
        map.put("time_shift", meta("日期时间", "基于当前时间按 ISO-8601 偏移量平移后格式化输出", "${time_shift([format,] offset)}", "${time_shift(+1d)}",
                param("format", false, "输出日期格式，默认 yyyy-MM-dd HH:mm:ss"),
                param("offset", true, "ISO-8601 偏移量，如 +1d / -2h")));
        map.put("json", meta("数据处理", "将多组 k=v 参数组装为 JSON 字符串", "${json(k1=v1, k2=v2)}", "${json(code=0, msg=ok)}",
                param("k=v", true, "键值参数，至少一组")));
        map.put("json_read", meta("数据处理", "从 JSON 文本中按 JsonPath 提取值", "${json_read(json, jsonpath)}", "${json_read(${json(id=1)}, $.id)}",
                param("json", true, "JSON 文本或变量引用"),
                param("jsonpath", true, "JsonPath 表达式")));
        map.put("url_encode", meta("数据处理", "URL 编码（application/x-www-form-urlencoded）", "${url_encode(content)}", "${url_encode(a b&c=1)}",
                param("content", true, "待编码文本")));
        map.put("url_decode", meta("数据处理", "URL 解码", "${url_decode(content)}", "${url_decode(%E4%B8%AD%E6%96%87)}",
                param("content", true, "待解码文本")));
        map.put("base64_encode", meta("数据处理", "Base64 编码", "${base64_encode(content)}", "${base64_encode(robotest)}",
                param("content", true, "待编码文本")));
        map.put("base64_decode", meta("数据处理", "Base64 解码", "${base64_decode(content)}", "${base64_decode(cm9ib3Rlc3Q=)}",
                param("content", true, "待解码 Base64 文本")));
        map.put("property", meta("数据处理", "读取平台变量值（就近作用域解析）", "${property(key)}", "${property(token)}",
                param("key", true, "变量名")));
        map.put("digest", meta("安全加密", "摘要算法（md5/sha-1/sha-256 等，支持盐值）", "${digest(algorithm, content[, salt])}", "${digest(md5, password, salt123)}",
                param("algorithm", true, "摘要算法名"),
                param("content", true, "原文"),
                param("salt", false, "盐值")));
        map.put("google2fa", meta("安全加密", "根据 2FA 密钥生成 Google 验证码", "${google2fa(secretKey)}", "${google2fa(JBSWY3DPEHPK3PXP)}",
                param("secretKey", true, "Google Authenticator 共享密钥")));
        return map;
    }
}
