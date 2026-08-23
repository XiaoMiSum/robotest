package io.github.xiaomisum.robotest.service.apitest.imports;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JMeter .jmx 解析（手写 XML 子集，详细设计 6.3）：HTTPSamplerProxy + HeaderManager 常用属性
 */
public class JmeterImportParser implements InterfaceImportParser {

    @Override
    public String sourceType() {
        return "jmeter_sampler";
    }

    @Override
    public boolean supports(String formatHint, String content) {
        if ("jmeter".equals(formatHint)) {
            return true;
        }
        return content.contains("<jmeterTestPlan") && content.contains("HTTPSamplerProxy");
    }

    @Override
    public List<ImportedOperation> parse(String content) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            NodeList samplers = document.getElementsByTagName("HTTPSamplerProxy");
            List<ImportedOperation> operations = new ArrayList<>();
            for (int i = 0; i < samplers.getLength(); i++) {
                Element sampler = (Element) samplers.item(i);
                operations.add(toOperation(sampler, i));
            }
            if (operations.isEmpty()) {
                throw new IllegalArgumentException("未解析到 HTTPSamplerProxy 取样器");
            }
            return operations;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("jmx 解析失败：" + exception.getMessage(), exception);
        }
    }

    private ImportedOperation toOperation(Element sampler, int index) {
        Map<String, String> props = elementProps(sampler);
        // JMeter 路径可能含域名（无独立 domain 时），统一拆出路径段
        String rawPath = props.getOrDefault("HTTPSampler.path", "/");
        String path = extractPath(rawPath);
        String method = props.getOrDefault("HTTPSampler.method", "GET").toUpperCase();
        String name = sampler.getAttribute("testname");
        boolean postBodyRaw = Boolean.parseBoolean(props.getOrDefault("HTTPSampler.postBodyRaw", "false"));

        List<Map<String, Object>> headers = new ArrayList<>();
        collectHeaders(sampler.getElementsByTagName("HeaderManager"), headers);

        List<Map<String, Object>> query = new ArrayList<>();
        Map<String, Object> formBody = new LinkedHashMap<>();
        String rawBody = null;
        NodeList arguments = sampler.getElementsByTagName("argument");
        for (int a = 0; a < arguments.getLength(); a++) {
            Element argument = (Element) arguments.item(a);
            Map<String, String> argumentProps = elementProps(argument);
            String key = argumentProps.getOrDefault("Argument.name", argument.getAttribute("name"));
            if ("HTTPSampler.arguments".equals(argument.getAttribute("name"))) {
                continue;
            }
            if (postBodyRaw && method.matches("POST|PUT|PATCH|DELETE")) {
                rawBody = argumentProps.getOrDefault("Argument.value", "");
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("value", argumentProps.getOrDefault("Argument.value", ""));
            item.put("enabled", true);
            if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))
                    && Boolean.parseBoolean(props.getOrDefault("HTTPSampler.postBodyRaw", "false"))) {
                // 已按 raw 处理
                continue;
            }
            query.add(item);
            if (!key.isEmpty()) {
                formBody.put(key, item.get("value"));
            }
        }

        Map<String, Object> body = null;
        if (rawBody != null) {
            body = new LinkedHashMap<>();
            body.put("type", looksLikeJson(rawBody) ? "json" : "raw");
            body.put("content", rawBody);
        } else if (!formBody.isEmpty()) {
            body = new LinkedHashMap<>();
            body.put("type", "form");
            body.put("content", formBody);
        }

        return ImportedOperation.builder()
                .sourceId(name.isEmpty() ? method + ":" + path : name + "#" + index)
                .sourceName(name.isEmpty() ? method + " " + path : name)
                .method(method)
                .path(path)
                .headers(headers)
                .queryParams(query)
                .body(body)
                .build();
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private void collectHeaders(NodeList headerManagers, List<Map<String, Object>> out) {
        for (int h = 0; h < headerManagers.getLength(); h++) {
            Element manager = (Element) headerManagers.item(h);
            NodeList headers = manager.getElementsByTagName("elementProp");
            for (int i = 0; i < headers.getLength(); i++) {
                Element element = (Element) headers.item(i);
                Map<String, String> props = elementProps(element);
                String key = props.getOrDefault("Header.name", "");
                if (key.isEmpty()) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", key);
                item.put("value", props.getOrDefault("Header.value", ""));
                item.put("enabled", true);
                out.add(item);
            }
        }
    }

    /** 展开 stringProp/boolProp/intProp 等子节点为 name→value 平面表 */
    private Map<String, String> elementProps(Element parent) {
        Map<String, String> props = new LinkedHashMap<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) {
                continue;
            }
            String tagName = child.getTagName();
            if (tagName.endsWith("Prop") || "elementProp".equals(tagName)) {
                String name = child.getAttribute("name");
                if (!name.isEmpty()) {
                    props.put(name, child.getTextContent().strip());
                }
            }
        }
        return props;
    }

    private String extractPath(String rawPath) {
        int schemeEnd = rawPath.indexOf("://");
        if (schemeEnd >= 0) {
            int pathStart = rawPath.indexOf('/', schemeEnd + 3);
            return pathStart < 0 ? "/" : rawPath.substring(pathStart);
        }
        int query = rawPath.indexOf('?');
        String path = query < 0 ? rawPath : rawPath.substring(0, query);
        return path.isEmpty() ? "/" : path;
    }
}
