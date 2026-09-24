package io.github.xiaomisum.robotest.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEC-002 守卫：/api/admin/** 的每个映射方法必须声明 @PreAuthorize，
 * 防止新增管理端接口漏加服务端权限校验（安全规范 §3.1：不能仅依赖前端路由守卫）
 */
class AdminAuthorizationGuardTest {

    private static final String ADMIN_PACKAGE = "io.github.xiaomisum.robotest.controller.admin";

    @Test
    void allAdminMappingMethodsDeclarePreAuthorize() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> violations = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ADMIN_PACKAGE)) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            if (controller.isAnnotationPresent(PreAuthorize.class)) {
                continue;
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (isMappingMethod(method) && !method.isAnnotationPresent(PreAuthorize.class)) {
                    violations.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "以下管理端接口缺少 @PreAuthorize 服务端权限校验：" + violations);
    }

    private static boolean isMappingMethod(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }
}
