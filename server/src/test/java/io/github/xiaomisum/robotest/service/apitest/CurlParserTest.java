package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurlParserTest {

    @Test
    void parseFullCommandExtractsMethodUrlHeadersBody() {
        String cmd = """
                curl -X POST 'https://staging.example.com/api/auth/login' \\
                  -H 'Content-Type: application/json' \\
                  -H 'Authorization: Bearer abc123' \\
                  -d '{"username":"admin","password":"123456"}'
                """;
        CurlParser.ParsedCurl parsed = CurlParser.parse(cmd);
        assertThat(parsed.method()).isEqualTo("POST");
        assertThat(parsed.url()).isEqualTo("https://staging.example.com/api/auth/login");
        assertThat(parsed.headers()).hasSize(2);
        assertThat(parsed.headers().get(0))
                .containsEntry("key", "Content-Type")
                .containsEntry("value", "application/json")
                .containsEntry("enabled", true);
        assertThat(parsed.bodyType()).isEqualTo("json");
        assertThat(parsed.bodyContent()).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) parsed.bodyContent()).containsEntry("username", "admin");
    }

    @Test
    void dataImpliesPostWhenMethodOmitted() {
        var parsed = CurlParser.parse("curl https://example.com/api -d 'k=v'");
        assertThat(parsed.method()).isEqualTo("POST");
    }

    @Test
    void defaultMethodIsGetWithoutData() {
        var parsed = CurlParser.parse("curl https://example.com/api/users?page=1");
        assertThat(parsed.method()).isEqualTo("GET");
        assertThat(parsed.bodyType()).isNull();
    }

    @Test
    void formFieldsMapToFormBodyAndFileUploadsDropped() {
        var parsed = CurlParser.parse(
                "curl -X POST https://example.com/upload -F 'name=robotest' -F 'file=@/tmp/a.png'");
        assertThat(parsed.bodyType()).isEqualTo("form");
        Map<String, Object> content = (Map<String, Object>) parsed.bodyContent();
        assertThat(content).containsEntry("name", "robotest").doesNotContainKey("file");
    }

    @Test
    void cookieMapsToCookieHeader() {
        var parsed = CurlParser.parse("curl https://example.com/ -b 'SESSION=abc; THEME=dark'");
        assertThat(parsed.headers()).anySatisfy(h -> {
            assertThat(h).containsEntry("key", "Cookie").containsEntry("value", "SESSION=abc; THEME=dark");
        });
    }

    @Test
    void nonJsonDataFallsBackToRawString() {
        var parsed = CurlParser.parse("curl -X POST https://example.com -d 'a=1&b=2'");
        assertThat(parsed.bodyType()).isEqualTo("json");
        assertThat(parsed.bodyContent()).isEqualTo("a=1&b=2");
    }

    @Test
    void unsupportedFlagsIgnoredSilently() {
        var parsed = CurlParser.parse(
                "curl --proxy http://127.0.0.1:8888 -k -L -s https://example.com/api --cert ./a.pem");
        assertThat(parsed.url()).isEqualTo("https://example.com/api");
    }

    @Test
    void missingUrlThrowsIllegalArgument() {
        assertThatThrownBy(() -> CurlParser.parse("curl -X POST -H 'A: b'"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doubleQuoteEscapeAndConcatenatedQuoting() {
        List<String> tokens = CurlParser.tokenize("curl -d '{\"a\":\"x y\"}' -H \"X-We\\\"ird: 1\"");
        assertThat(tokens).containsExactly("curl", "-d", "{\"a\":\"x y\"}", "-H", "X-We\"ird: 1");
    }
}
