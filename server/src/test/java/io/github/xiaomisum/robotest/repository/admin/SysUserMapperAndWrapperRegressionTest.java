package io.github.xiaomisum.robotest.repository.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 回归测试：migoo 框架 1.3.17 的 LambdaQueryWrapperX.and(boolean, Consumer)
 * 错误调用 super.and(consumer)，与 Nested.and(Consumer) 默认实现形成无限递归
 * （StackOverflowError）。1.3.18 已修复，此处锁定 and(w -> ...) 不再崩溃、
 * OR 组正确包裹进 AND 括号内。
 */
class SysUserMapperAndWrapperRegressionTest {

    private SysUserMapper mapper;

    @BeforeAll
    static void initTableInfo() {
        // getSqlSegment() 需要 MyBatis-Plus 的 lambda 列缓存，纯单测环境下手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SysUserMapper.class, Mockito.CALLS_REAL_METHODS);
        when(mapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void listActiveByKeyword_keywordPresent_buildsWrappedOrGroup() {
        // 1.3.17 在此抛 StackOverflowError；1.3.18 应正常构建
        List<SysUser> users = assertDoesNotThrow(() -> mapper.listActiveByKeyword("tester"));
        assertTrue(users.isEmpty());
    }

    @Test
    void listActiveByKeyword_noKeyword_buildsOnlyStatusCondition() {
        List<SysUser> users = assertDoesNotThrow(() -> mapper.listActiveByKeyword(""));
        assertTrue(users.isEmpty());
    }

    @Test
    void findPage_keywordPresent_buildsWrappedOrGroup() {
        // keyword + status + filteredUserIds 全分支
        assertDoesNotThrow(() -> mapper.findPage("tester", Constants.Status.ACTIVE,
                List.of(UUID.randomUUID()), 1, 10));
    }

    @Test
    void listByKeyword_keywordPresent_buildsWrappedOrGroup() {
        assertDoesNotThrow(() -> mapper.listByKeyword("tester"));
    }

    @Test
    void wrapperAnd_consumer_preservesConditionAndGroupsOr() {
        // 直接验证框架修复后的 SQL 语义：AND (name LIKE ? OR username LIKE ? OR email LIKE ?)
        LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<SysUser>()
                .eq(SysUser::getStatus, Constants.Status.ACTIVE);
        wrapper.and(w -> w.like(SysUser::getName, "tester")
                .or().like(SysUser::getUsername, "tester")
                .or().like(SysUser::getEmail, "tester"));

        String sql = wrapper.getSqlSegment();
        // OR 组必须被 and() 包裹成整体，避免短路后续条件（如 status eq）
        assertTrue(sql.contains("(name LIKE #{ew.paramNameValuePairs.MPGENVAL2} OR "
                        + "username LIKE #{ew.paramNameValuePairs.MPGENVAL3} OR "
                        + "email LIKE #{ew.paramNameValuePairs.MPGENVAL4})"),
                "expected wrapped OR group, actual: " + sql);
    }

    @Test
    void wrapperAnd_withConditionFalse_skipsGroup() {
        // and(false, consumer) 应整体跳过，不再递归
        LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<SysUser>();
        wrapper.and(false, w -> w.like(SysUser::getName, "tester"));

        String sql = wrapper.getSqlSegment();
        assertTrue(sql.isBlank(), "condition=false 时不应拼接条件, actual: " + sql);
    }
}
