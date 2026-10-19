package io.github.xiaomisum.robotest.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * schema.sql 权限种子一致性守卫：前端按权限码显隐入口、Controller 按 @PreAuthorize 收口，
 * 但 WorkspaceRoleInterceptor 只注入 sys_role.permissions 中登记的码。种子缺码时角色单测
 * （mock 权限列表）依然通过，页面却永久无入口，故直接对全量种子断言。
 */
class SchemaSeedConsistencyTest {

    private static final String SCHEMA = "db/schema.sql";

    /** 预置权限点编号 c0000000-…-0012（18.3 业务权限点段） */
    private static final String PROJECT_CREATE_ROW = "'c0000000-0000-0000-0000-000000000012', 'project:create'";

    private static final String ADMIN_ROLE_ID = "'c0000000-0000-0000-0000-000000000001'";

    private static final String MEMBER_ROLE_ID = "'c0000000-0000-0000-0000-000000000002'";

    private static final String PROJECT_CREATE_CODE = "\"project:create\"";

    @Test
    void schemaRegistersProjectCreateAndGrantsPresetWorkspaceRoles() throws IOException {
        String sql = loadSchema();

        assertTrue(sql.contains(PROJECT_CREATE_ROW),
                "schema.sql 缺少 project:create 权限点，项目列表页新建入口与创建接口将无码可用");

        String roleSeed = presetRoleSeed(sql);
        int admin = roleSeed.indexOf(ADMIN_ROLE_ID);
        int member = roleSeed.indexOf(MEMBER_ROLE_ID);
        assertTrue(admin >= 0 && member > admin, "schema.sql 预置空间角色（管理员/成员）顺序或编号已变更，守卫需同步");

        assertTrue(roleSeed.substring(admin, member).contains(PROJECT_CREATE_CODE),
                "预置「管理员」角色未授予 project:create，空间管理员将看不到新建项目入口");
        assertTrue(roleSeed.substring(member).contains(PROJECT_CREATE_CODE),
                "预置「成员」角色未授予 project:create，普通成员将看不到新建项目入口");
    }

    private String loadSchema() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SCHEMA)) {
            assertNotNull(in, "classpath 未找到 " + SCHEMA);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 截取 sys_role 预置角色 INSERT 语句（单语句，至首个分号结束） */
    private String presetRoleSeed(String sql) {
        int start = sql.indexOf("INSERT INTO sys_role");
        assertTrue(start >= 0, "schema.sql 未找到 sys_role 预置角色种子");
        int end = sql.indexOf(';', start);
        assertTrue(end > start, "sys_role 种子语句未以分号结束");
        return sql.substring(start, end);
    }
}
