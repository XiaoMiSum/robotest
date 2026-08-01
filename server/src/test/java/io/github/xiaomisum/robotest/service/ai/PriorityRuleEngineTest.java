package io.github.xiaomisum.robotest.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 优先级规则引擎单测（详细设计 4.3）：按 P0 → P1 → P3 短路匹配，未命中返回 null。
 */
class PriorityRuleEngineTest {

    private PriorityRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PriorityRuleEngine();
    }

    @Test
    void match_p0Keywords() {
        for (String title : new String[]{"用户登录失败", "支付失败重试", "下单流程校验", "重置密码",
                "权限校验", "安全防护", "并发数据丢失", "系统崩溃恢复"}) {
            assertEquals("P0", engine.match(title), title);
        }
    }

    @Test
    void match_p1Keywords() {
        for (String title : new String[]{"提交订单", "保存草稿", "删除用例", "审核通过",
                "导出报表", "数据同步"}) {
            assertEquals("P1", engine.match(title), title);
        }
    }

    @Test
    void match_p3Keywords() {
        for (String title : new String[]{"提示文案优化", "按钮样式调整", "表格排版", "按钮颜色",
                "输入框占位符"}) {
            assertEquals("P3", engine.match(title), title);
        }
    }

    @Test
    void match_shortCircuitsByOrder_p0BeatsLowerPriorities() {
        // 同时含 P3 与 P1 关键词 → 短路返回 P1；同时含 P1 与 P0 → 短路返回 P0
        assertEquals("P1", engine.match("提交订单样式调整"));
        assertEquals("P0", engine.match("登录支付占位符"));
    }

    @Test
    void match_noKeyword_returnsNull() {
        assertNull(engine.match("常规流程校验"));
        assertNull(engine.match("功能可用性测试"));
    }

    @Test
    void match_blankOrNull_returnsNull() {
        assertNull(engine.match(null));
        assertNull(engine.match(""));
        assertNull(engine.match("   "));
    }
}
