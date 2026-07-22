package io.github.xiaomisum.robotest.framework.audit;

import java.lang.annotation.*;

/**
 * 标记需要审计日志的方法。
 * 方法参数中第一个 String 类型参数会被视为 entityId。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditOperation {

    /** 操作类型：CREATE / UPDATE / DELETE / EXPORT */
    String operation();

    /** 业务实体类型，如 "TestCaseNode"、"TestPlan" */
    String entityType();

    /** 是否记录方法参数（默认 true），传 false 则 changes 为空 */
    boolean logParams() default true;
}
