package io.github.xiaomisum.robotest.service.apitest.imports;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Swagger/OpenAPI 2.0/3.0 解析（JSON/YAML，含 $ref），委托官方 swagger-parser
 */
public class SwaggerImportParser implements InterfaceImportParser {

    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete", "options", "head");

    @Override
    public String sourceType() {
        return "swagger_operation";
    }

    @Override
    public boolean supports(String formatHint, String content) {
        if ("swagger".equals(formatHint) || "openapi".equals(formatHint)) {
            return true;
        }
        String head = content.stripLeading();
        return head.startsWith("{") && (head.contains("\"swagger\"") || head.contains("\"openapi\""))
                || (head.startsWith("#") || head.startsWith("openapi:") || head.startsWith("swagger:"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ImportedOperation> parse(String content) {
        OpenAPI api = new OpenAPIParser().readContents(content, null, null).getOpenAPI();
        if (api == null || api.getPaths() == null) {
            throw new IllegalArgumentException("未解析到任何路径定义");
        }
        List<ImportedOperation> operations = new ArrayList<>();
        api.getPaths().forEach((path, pathItem) -> collectOperations(api, path, pathItem, operations));
        return operations;
    }

    private void collectOperations(OpenAPI api, String path, PathItem pathItem, List<ImportedOperation> out) {
        for (String method : METHODS) {
            Operation operation = pathItem.readOperationsMap().get(PathItem.HttpMethod.valueOf(method.toUpperCase()));
            if (operation == null) {
                continue;
            }
            Map<String, Object> body = extractBody(operation);
            ImportedOperation.ImportedOperationBuilder builder = ImportedOperation.builder()
                    .sourceId(StringUtils.defaultIfBlank(operation.getOperationId(), method + ":" + path))
                    .sourceName(StringUtils.defaultIfBlank(operation.getSummary(), method.toUpperCase() + " " + path))
                    .method(method.toUpperCase())
                    .path(path)
                    .description(operation.getDescription())
                    .headers(new ArrayList<>())
                    .queryParams(extractQueryParams(operation))
                    .body(body);
            // 3.0 响应示例取首个成功响应的说明级内容；2.0 由解析器统一转换后结构一致
            ApiResponse ok = operation.getResponses() == null ? null : operation.getResponses().get("200");
            if (ok != null && ok.getContent() != null && !ok.getContent().isEmpty()) {
                Map<String, Object> example = new LinkedHashMap<>();
                example.put("status", 200);
                example.put("description", ok.getDescription());
                builder.description(StringUtils.defaultIfBlank(operation.getDescription(), ok.getDescription()));
            }
            out.add(builder.build());
        }
    }

    private List<Map<String, Object>> extractQueryParams(Operation operation) {
        List<Map<String, Object>> params = new ArrayList<>();
        if (operation.getParameters() == null) {
            return params;
        }
        for (Parameter parameter : operation.getParameters()) {
            if (!"query".equals(parameter.getIn())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", parameter.getName());
            item.put("value", "");
            item.put("enabled", true);
            params.add(item);
        }
        return params;
    }

    /** 请求体仅提取 JSON 结构骨架，示例值留给调试时填充 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractBody(Operation operation) {
        io.swagger.v3.oas.models.parameters.RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (requestBody.getContent().containsKey("application/json")) {
            result.put("type", "json");
            result.put("content", sampleFromSchema(requestBody.getContent().get("application/json").getSchema(), operation));
        } else if (requestBody.getContent().containsKey("application/x-www-form-urlencoded")
                || requestBody.getContent().containsKey("multipart/form-data")) {
            result.put("type", "form");
            result.put("content", new LinkedHashMap<>());
        } else {
            result.put("type", "raw");
            result.put("content", "");
        }
        return result;
    }

    /** 从 schema 生成最小示例对象：对象递归一层字段、数组取单元素 */
    private Object sampleFromSchema(io.swagger.v3.oas.models.media.Schema<?> schema, Operation operation) {
        if (schema == null) {
            return "";
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            Map<String, Object> obj = new LinkedHashMap<>();
            schema.getProperties().forEach((name, child) -> obj.put(name,
                    child instanceof io.swagger.v3.oas.models.media.Schema<?> s ? sampleFromSchema(s, operation) : ""));
            return obj;
        }
        return StringUtils.defaultIfBlank(schema.getExample() == null ? null : String.valueOf(schema.getExample()), "");
    }
}
