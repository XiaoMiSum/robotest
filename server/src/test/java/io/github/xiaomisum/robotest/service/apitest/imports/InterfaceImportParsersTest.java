package io.github.xiaomisum.robotest.service.apitest.imports;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 四种导入格式解析器：fixture 驱动的解析行为验证 */
class InterfaceImportParsersTest {

    private final SwaggerImportParser swaggerParser = new SwaggerImportParser();
    private final PostmanImportParser postmanParser = new PostmanImportParser();
    private final HarImportParser harParser = new HarImportParser();
    private final JmeterImportParser jmeterParser = new JmeterImportParser();

    // ==================== Swagger ====================

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
        assertThat(get.getPath()).isEqualTo("/pets/{id}");
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

    // ==================== Postman ====================

    @Test
    void postmanParsesNestedItemsWithRawBodyAndQuery() {
        String content = """
                {
                  "info": {"name": "col", "schema": "https://schema.getpostman.com/json/collection/v2.1.0"},
                  "item": [
                    {
                      "name": "认证",
                      "item": [
                        {
                          "name": "登录",
                          "id": "req-1",
                          "request": {
                            "method": "POST",
                            "url": {"raw": "https://api.example.com/auth/login?x=1",
                                    "query": [{"key": "x", "value": "1"}]},
                            "header": [{"key": "Content-Type", "value": "application/json"}],
                            "body": {"mode": "raw", "raw": "{\\"username\\": \\"admin\\"}"}
                          }
                        }
                      ]
                    }
                  ]
                }
                """;
        assertThat(postmanParser.supports(null, content)).isTrue();
        List<ImportedOperation> operations = postmanParser.parse(content);

        assertThat(operations).hasSize(1);
        ImportedOperation op = operations.get(0);
        assertThat(op.getSourceId()).isEqualTo("req-1");
        assertThat(op.getSourceName()).isEqualTo("登录");
        assertThat(op.getPath()).isEqualTo("/auth/login");
        assertThat(op.getQueryParams()).extracting(p -> p.get("key")).containsExactly("x");
        assertThat(op.getHeaders()).extracting(h -> h.get("key")).containsExactly("Content-Type");
        assertThat(op.getBody().get("type")).isEqualTo("json");
        assertThat(op.getBody().get("content")).isEqualTo(Map.of("username", "admin"));
    }

    @Test
    void postmanParsesFormdataAndUrlencodedBodies() {
        String content = """
                {
                  "info": {"name": "col"},
                  "item": [
                    {
                      "name": "上传",
                      "request": {
                        "method": "POST",
                        "url": "https://api.example.com/upload",
                        "body": {
                          "mode": "urlencoded",
                          "urlencoded": [{"key": "file", "value": "a.zip"}]
                        }
                      }
                    }
                  ]
                }
                """;
        List<ImportedOperation> operations = postmanParser.parse(content);
        assertThat(operations.get(0).getBody().get("type")).isEqualTo("form");
        assertThat((List<?>) operations.get(0).getBody().get("content"))
                .first()
                .hasFieldOrPropertyWithValue("key", "file");
    }

    // ==================== HAR ====================

    @Test
    void harParsesEntriesWithJsonAndFormPostData() {
        String content = """
                {
                  "log": {
                    "entries": [
                      {
                        "request": {
                          "method": "GET",
                          "url": "https://a.example.com/x/y?b=2",
                          "queryString": [{"name": "b", "value": "2"}],
                          "headers": [{"name": "Accept", "value": "*/*"}]
                        }
                      },
                      {
                        "request": {
                          "method": "POST",
                          "url": "https://a.example.com/z",
                          "postData": {"mimeType": "application/x-www-form-urlencoded", "text": "k=1&n=%E4%B8%AD"}
                        }
                      }
                    ]
                  }
                }
                """;
        assertThat(harParser.supports(null, content)).isTrue();
        List<ImportedOperation> operations = harParser.parse(content);

        assertThat(operations).hasSize(2);
        ImportedOperation first = operations.get(0);
        assertThat(first.getMethod()).isEqualTo("GET");
        assertThat(first.getPath()).isEqualTo("/x/y");
        assertThat(first.getQueryParams()).extracting(q -> q.get("value")).containsExactly("2");

        ImportedOperation second = operations.get(1);
        assertThat(second.getBody().get("type")).isEqualTo("form");
        assertThat(second.getBody().get("content")).isEqualTo(Map.of("k", "1", "n", "中"));
    }

    // ==================== JMeter ====================

    @Test
    void jmeterParsesSamplersArgumentsHeadersAndRawBody() {
        String content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="list users">
                      <boolProp name="HTTPSampler.postBodyRaw">false</boolProp>
                      <stringProp name="HTTPSampler.path">/users</stringProp>
                      <stringProp name="HTTPSampler.method">GET</stringProp>
                      <elementProp name="HTTPSampler.arguments" elementType="Arguments">
                        <collectionProp name="Arguments.arguments">
                          <elementProp name="page" elementType="HTTPArgument">
                            <stringProp name="Argument.name">page</stringProp>
                            <stringProp name="Argument.value">1</stringProp>
                          </elementProp>
                        </collectionProp>
                      </elementProp>
                    </HTTPSamplerProxy>
                    <HTTPSamplerProxy testclass="HTTPSamplerProxy" testname="create user">
                      <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
                      <stringProp name="HTTPSampler.path">/users</stringProp>
                      <stringProp name="HTTPSampler.method">POST</stringProp>
                      <elementProp name="HTTPSampler.arguments" elementType="Arguments">
                        <collectionProp name="Arguments.arguments">
                          <elementProp name="" elementType="HTTPArgument">
                            <stringProp name="Argument.value">{\\"name\\": \\"tom\\"}</stringProp>
                          </elementProp>
                        </collectionProp>
                      </elementProp>
                      <HeaderManager testclass="HeaderManager" testname="headers">
                        <collectionProp name="HeaderManager.headers">
                          <elementProp name="" elementType="Header">
                            <stringProp name="Header.name">X-Tag</stringProp>
                            <stringProp name="Header.value">jmx</stringProp>
                          </elementProp>
                        </collectionProp>
                      </HeaderManager>
                    </HTTPSamplerProxy>
                  </hashTree>
                </jmeterTestPlan>
                """;
        assertThat(jmeterParser.supports(null, content)).isTrue();
        List<ImportedOperation> operations = jmeterParser.parse(content);

        assertThat(operations).hasSize(2);
        ImportedOperation listUsers = operations.get(0);
        assertThat(listUsers.getMethod()).isEqualTo("GET");
        assertThat(listUsers.getPath()).isEqualTo("/users");
        assertThat(listUsers.getQueryParams()).extracting(q -> q.get("key")).containsExactly("page");
        assertThat(listUsers.getBody()).isNull();

        ImportedOperation createUser = operations.get(1);
        assertThat(createUser.getMethod()).isEqualTo("POST");
        assertThat(createUser.getHeaders()).extracting(h -> h.get("value")).containsExactly("jmx");
        assertThat(createUser.getBody().get("type")).isEqualTo("json");
    }

    @Test
    void jmeterThrowsWhenNoSamplers() {
        String content = "<jmeterTestPlan><hashTree/></jmeterTestPlan>";
        assertThrows(IllegalArgumentException.class, () -> jmeterParser.parse(content));
    }

    // ==================== 嗅探互斥 ====================

    @Test
    void sniffersRejectUnrelatedContent() {
        String garbage = "just some plain text";
        assertThat(swaggerParser.supports(null, garbage)).isFalse();
        assertThat(postmanParser.supports(null, garbage)).isFalse();
        assertThat(harParser.supports(null, garbage)).isFalse();
        assertThat(jmeterParser.supports(null, garbage)).isFalse();
    }
}
