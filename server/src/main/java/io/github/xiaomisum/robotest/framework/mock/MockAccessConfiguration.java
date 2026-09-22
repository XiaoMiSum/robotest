package io.github.xiaomisum.robotest.framework.mock;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Mock 免登录访问装配（Mock服务详细设计 6.1）。
 * 过滤器顺序先于安全过滤链，命中规则时短路响应，未命中放行平台链路。
 */
@Configuration
@ConditionalOnProperty(prefix = "robotest.api-test.mock", name = "access-enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MockAccessProperties.class)
public class MockAccessConfiguration {

    @Bean
    public MockAccessFilter mockAccessFilter(MockDefinitionReader reader,
                                             MockAccessProperties properties) {
        return new MockAccessFilter(reader, properties);
    }

    @Bean
    public FilterRegistrationBean<MockAccessFilter> mockAccessFilterRegistration(MockAccessFilter filter) {
        FilterRegistrationBean<MockAccessFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.addUrlPatterns("/*");
        registration.setName("mockAccessFilter");
        return registration;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> mockPortCustomizer(
            MockAccessProperties properties) {
        return factory -> {
            Integer port = properties.getPort();
            if (port == null || port <= 0) {
                return;
            }
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(port);
            factory.addAdditionalConnectors(connector);
        };
    }

}
