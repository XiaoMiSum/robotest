package io.github.xiaomisum.robotest.service.apitest.imports;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Swagger/OpenAPI 解析器：fixture 驱动的解析行为验证 */
class InterfaceImportParsersTest {

    private final SwaggerImportParser swaggerParser = new SwaggerImportParser();

    @Test
    void swaggerParsesOperationsQueryParamsAndJsonBody() {
        String content = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "pets", "version": "1.0"},
                  "paths": {
                    "/pets/{id}": {
                      "get": {
                        "operationId": "getPet",
                        "summary": "查询宠物",
                        "parameters": [
                          {"name": "id", "in": "path", "required": true, "schema": {"type": "integer"}},
                          {"name": "limit", "in": "query", "schema": {"type": "integer"}}
                        ],
                        "responses": {"200": {"description": "ok"}}
                      },
                      "post": {
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"type": "object", "properties": {"name": {"type": "string"}}}
                            }
                          }
                        },
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """;
        assertThat(swaggerParser.supports(null, content)).isTrue();
        List<ImportedOperation> operations = swaggerParser.parse(content);

        assertThat(operations).hasSize(2);
        ImportedOperation get = operations.get(0);
        assertThat(get.getMethod()).isEqualTo("GET");
        assertThat(get.getPath()).isEqualTo("/pets/${id}");
        assertThat(get.getSourceId()).isEqualTo("getPet");
        assertThat(get.getSourceName()).isEqualTo("查询宠物");
        assertThat(get.getQueryParams()).extracting(p -> p.get("key")).containsExactly("limit");

        ImportedOperation post = operations.get(1);
        assertThat(post.getMethod()).isEqualTo("POST");
        assertThat(post.getBody()).isNotNull();
        assertThat(post.getBody().get("type")).isEqualTo("json");
        @SuppressWarnings("unchecked")
        Map<String, Object> sample = (Map<String, Object>) post.getBody().get("content");
        assertThat(sample).containsKey("name");
    }

    @Test
    void swaggerNormalizesPathTemplateParams() {
        String content = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "members", "version": "1.0"},
                  "paths": {
                    "/api/workspace/members/{userId}": {
                      "delete": {
                        "operationId": "removeMember",
                        "parameters": [
                          {"name": "userId", "in": "path", "required": true, "schema": {"type": "string"}}
                        ],
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """;
        List<ImportedOperation> operations = swaggerParser.parse(content);
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getPath()).isEqualTo("/api/workspace/members/${userId}");
    }

    @Test
    void swaggerFallsBackToOperationIdWhenSummaryMissing() {
        String content = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "members", "version": "1.0"},
                  "paths": {
                    "/api/workspace/members/{userId}": {
                      "delete": {
                        "operationId": "removeMember",
                        "parameters": [
                          {"name": "userId", "in": "path", "required": true, "schema": {"type": "string"}}
                        ],
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """;
        ImportedOperation op = swaggerParser.parse(content).get(0);
        assertThat(op.getSourceName()).isEqualTo("removeMember");
        assertThat(op.getSourceId()).isEqualTo("removeMember");
    }

    @Test
    void swaggerParsesYamlByContentSniffing() {
        String yaml = """
                openapi: 3.0.0
                info:
                  title: ping
                  version: "1"
                paths:
                  /ping:
                    get:
                      operationId: ping
                      responses:
                        '200':
                          description: ok
                """;
        assertThat(swaggerParser.supports(null, yaml)).isTrue();
        List<ImportedOperation> operations = swaggerParser.parse(yaml);
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getPath()).isEqualTo("/ping");
        assertThat(operations.get(0).getMethod()).isEqualTo("GET");
    }

    @Test
    void snifferRejectsUnrelatedContent() {
        String garbage = "just some plain text";
        assertThat(swaggerParser.supports(null, garbage)).isFalse();
    }
}