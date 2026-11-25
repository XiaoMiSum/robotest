-- ============================================================
-- RoboTest 多产物 AI 任务演示数据（本地开发用，禁止用于生产）
--
-- 内容：1 个 case_optimize 任务 + 1 条 succeeded 执行 + 5 条待处置产物。
--       用于验收业务面板「单条直出 / 采纳后自动切换下一条」与
--       「项目 / 现有内容 / AI 建议」三列对比（含无变化项折叠）。
--
-- 目标库：本地开发库（dev profile 默认 jdbc:postgresql://localhost:5432/robotest）
-- 前置条件：已执行 scripts/db/seed-demo.sql（提供工作空间、项目、用户与用例节点）
--
-- 执行方式：
--   docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 < scripts/db/seed-ai-multi-artifact.sql
--
-- 可重复执行：本脚本数据主键统一使用 '5eedb' 前缀，重跑时先按前缀清理。
-- 查看入口：项目 → 功能测试 → 脑图用例优化面板（任务详情可只读查看提案产物与审核结果）
-- ============================================================

BEGIN;

-- 守卫：依赖 seed-demo 的基础数据与 5 个真实用例节点（采纳动作要回写这些节点）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ws_workspace WHERE id = '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7' AND is_deleted = FALSE)
        OR NOT EXISTS (SELECT 1 FROM ws_project WHERE id = '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a' AND is_deleted = FALSE)
        OR (SELECT COUNT(*) FROM ft_case_node
            WHERE id IN ('5eed005e-0000-4000-8000-000000000000',
                         '5eed0065-0000-4000-8000-000000000000',
                         '5eed006a-0000-4000-8000-000000000000',
                         '5eed0071-0000-4000-8000-000000000000',
                         '5eed0079-0000-4000-8000-000000000000')
              AND is_deleted = FALSE) <> 5 THEN
        RAISE EXCEPTION '缺少演示基础数据：请先执行 scripts/db/seed-demo.sql'
            USING HINT = '需要示例空间、Robotest 项目与 5 个登录相关用例节点';
    END IF;
END $$;

-- 幂等清理：只删除本脚本前缀的数据，不影响 seed-demo 既有数据
DELETE FROM ai_task_adoption   WHERE task_id::text LIKE '5eedb%';
DELETE FROM ai_task_trace_event WHERE execution_id::text LIKE '5eedb%';
DELETE FROM ai_task_proposal   WHERE task_id::text LIKE '5eedb%';
DELETE FROM ai_task_execution  WHERE task_id::text LIKE '5eedb%';
DELETE FROM ai_task            WHERE id::text LIKE '5eedb%';

-- ============================================================
-- 1. 任务本体：status 为 proposals_ready，表示执行完成、产物待业务面板处置
-- ============================================================
INSERT INTO ai_task (
    id, type, title, status, project_id, workspace_id, submitted_by,
    input, result_meta, tokens_in, tokens_out
) VALUES (
    '5eedb001-0000-4000-8000-000000000000',
    'case_optimize',
    '登录相关用例优化（多产物示例）',
    'proposals_ready',
    '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a',
    '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7',
    '7e57001f-0000-4000-8000-000000000002',
    '{"scope":"login","nodeIds":["5eed005e-0000-4000-8000-000000000000","5eed0065-0000-4000-8000-000000000000","5eed006a-0000-4000-8000-000000000000","5eed0071-0000-4000-8000-000000000000","5eed0079-0000-4000-8000-000000000000"]}'::jsonb,
    '{"generatedArtifacts":5,"source":"mock-seed"}'::jsonb,
    1800, 4200
);

-- ============================================================
-- 2. 执行记录：seq=1 且 status=succeeded 表示执行完成
-- ============================================================
INSERT INTO ai_task_execution (
    id, task_id, seq, status, started_by, prompt_scene, plan, budget,
    steps, tokens_in, tokens_out, cost, duration_ms, heartbeat_at
) VALUES (
    '5eedb002-0000-4000-8000-000000000000',
    '5eedb001-0000-4000-8000-000000000000',
    1,
    'succeeded',
    '7e57001f-0000-4000-8000-000000000002',
    'case_optimize',
    '{"steps":["解析用例上下文","生成优化建议"]}'::jsonb,
    '{"maxSteps":40,"maxTokens":200000,"maxCost":5}'::jsonb,
    5, 1800, 4200, 0.28, 96000, CURRENT_TIMESTAMP
);

-- ============================================================
-- 3. 五条产物（pending）：业务面板单条直出，采纳/驳回后自动切换下一条
--    content.fields 驱动三列对比；changed 由前端比对 existing/suggested 得出
-- ============================================================
INSERT INTO ai_task_proposal (
    id, task_id, execution_id, proposal_key, kind, parent_key,
    title, content, confirm_status
) VALUES
(
    '5eedb011-0000-4000-8000-000000000000',
    '5eedb001-0000-4000-8000-000000000000',
    '5eedb002-0000-4000-8000-000000000000',
    'item-1', 'case_optimize_suggestion', NULL,
    '正确账号密码登录成功',
    '{
      "nodeId":"5eed005e-0000-4000-8000-000000000000",
      "fields":{
        "precondition":{"existing":"已在系统初始化中创建账号 demo","suggested":"已在系统初始化中创建账号 demo，且浏览器无有效会话"},
        "steps":{"existing":["打开登录页 /login","输入 demo 与正确密码","点击登录"],"suggested":["打开登录页 /login","输入 demo 与正确密码","点击登录","登录后检查会话 Cookie 标志"]},
        "expected":{"existing":["登录成功并跳转工作台首页"],"suggested":["登录成功并跳转工作台首页","会话 Cookie 带 HttpOnly 与 Secure 标志"]},
        "tags":{"existing":["登录"],"suggested":["登录","安全"]}
      },
      "priority":{"current":"P1","suggested":"P0","reason":"登录主链路，安全属性缺失将影响全部业务"},
      "sourceRefs":[{"id":"SEC-001","title":"安全基线要求","quote":"会话 Cookie 必须设置 HttpOnly 与 Secure"}]
    }'::jsonb,
    'pending'
),
(
    '5eedb012-0000-4000-8000-000000000000',
    '5eedb001-0000-4000-8000-000000000000',
    '5eedb002-0000-4000-8000-000000000000',
    'item-2', 'case_optimize_suggestion', NULL,
    '密码错误给出明确提示',
    '{
      "nodeId":"5eed0065-0000-4000-8000-000000000000",
      "fields":{
        "precondition":{"existing":"账号 demo 存在且密码已知错误","suggested":"账号 demo 存在且密码已知错误"},
        "steps":{"existing":["打开登录页 /login","输入 demo 与错误密码","点击登录"],"suggested":["打开登录页 /login","输入 demo 与错误密码","点击登录","对比不存在的账号提示文案"]},
        "expected":{"existing":["提示账号或密码错误"],"suggested":["提示账号或密码错误","提示文案不区分账号是否存在，避免账号枚举"]},
        "tags":{"existing":["登录"],"suggested":["登录","安全"]}
      },
      "priority":{"current":"P2","suggested":"P1","reason":"涉及账号枚举风险，需提升优先级"},
      "sourceRefs":[{"id":"SEC-002","title":"认证安全规范","quote":"认证失败提示不得暴露账号存在性"}]
    }'::jsonb,
    'pending'
),
(
    '5eedb013-0000-4000-8000-000000000000',
    '5eedb001-0000-4000-8000-000000000000',
    '5eedb002-0000-4000-8000-000000000000',
    'item-3', 'case_optimize_suggestion', NULL,
    '连续失败触发限流锁定',
    '{
      "nodeId":"5eed006a-0000-4000-8000-000000000000",
      "fields":{
        "precondition":{"existing":"账号 demo 存在","suggested":"账号 demo 存在，且无近期限流记录"},
        "steps":{"existing":["连续输入错误密码 5 次"],"suggested":["连续输入错误密码 5 次","第 6 次输入正确密码"]},
        "expected":{"existing":["第 6 次提示账号锁定"],"suggested":["第 6 次仍提示账号锁定","解锁时间为首次锁定后 15 分钟"]},
        "tags":{"existing":["登录","限流"],"suggested":["登录","限流"]}
      },
      "priority":{"current":"P0","suggested":"P0","reason":""},
      "sourceRefs":[]
    }'::jsonb,
    'pending'
),
(
    '5eedb014-0000-4000-8000-000000000000',
    '5eedb001-0000-4000-8000-000000000000',
    '5eedb002-0000-4000-8000-000000000000',
    'item-4', 'case_optimize_suggestion', NULL,
    '访问令牌过期自动刷新',
    '{
      "nodeId":"5eed0071-0000-4000-8000-000000000000",
      "fields":{
        "precondition":{"existing":"用户已登录","suggested":"用户已登录，且访问令牌剩余有效期小于 60 秒"},
        "steps":{"existing":["等待访问令牌过期","调用业务接口"],"suggested":["将访问令牌剩余有效期调整为 30 秒","调用业务接口","观察刷新令牌是否自动续期"]},
        "expected":{"existing":["接口返回 401"],"suggested":["接口自动续期后返回 200","刷新令牌仅可使用一次"]},
        "tags":{"existing":["鉴权"],"suggested":["鉴权","令牌"]}
      },
      "priority":{"current":"P2","suggested":"P1","reason":"令牌刷新失败会导致用户无感知掉线"},
      "sourceRefs":[{"id":"AUTH-014","title":"令牌刷新约定","quote":"刷新令牌一次性使用，续期后旧令牌立即失效"}]
    }'::jsonb,
    'pending'
),
(
    '5eedb015-0000-4000-8000-000000000000',
    '5eedb001-0000-4000-8000-000000000000',
    '5eedb002-0000-4000-8000-000000000000',
    'item-5', 'case_optimize_suggestion', NULL,
    '登出后旧令牌立即失效',
    '{
      "nodeId":"5eed0079-0000-4000-8000-000000000000",
      "fields":{
        "precondition":{"existing":"用户已登录并持有有效令牌","suggested":"用户已登录并持有有效令牌"},
        "steps":{"existing":["调用登出接口"],"suggested":["调用登出接口","使用登出前的令牌再次调用业务接口"]},
        "expected":{"existing":["登出成功"],"suggested":["登出成功","旧令牌调用返回 401"]},
        "tags":{"existing":["鉴权"],"suggested":["鉴权"]}
      },
      "priority":{"current":"P2","suggested":"P2","reason":""},
      "sourceRefs":[]
    }'::jsonb,
    'pending'
);

-- ============================================================
-- 4. 可选：AI 总开关。详情页在 available=false 时会显示「AI 能力未启用」降级页。
--    若本地已配置好 AI，可整段删除；脚本只补建缺失的单例配置并打开总开关。
-- ============================================================
INSERT INTO ai_config (id, enabled, default_model_id, task_timeout_seconds, task_max_retries)
SELECT '5eedb900-0000-4000-8000-000000000000',
       TRUE,
       (SELECT id FROM ai_model_config WHERE enabled = TRUE AND is_deleted = FALSE ORDER BY priority LIMIT 1),
       600,
       2
WHERE NOT EXISTS (SELECT 1 FROM ai_config WHERE is_deleted = FALSE);

UPDATE ai_config SET enabled = TRUE, updated_at = CURRENT_TIMESTAMP WHERE is_deleted = FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ai_model_config WHERE enabled = TRUE AND is_deleted = FALSE) THEN
        RAISE NOTICE '未找到已启用的 AI 模型：若详情页显示降级状态，请先在管理端配置并启用模型';
    END IF;
END $$;

COMMIT;

-- ============================================================
-- 验收提示
-- 1) 业务面板应只显示 1 张卡片（item-1），无左侧列表 / 全选 / 批量按钮；
-- 2) 卡片内三列对比：字段与优先级按既有 / 建议分区、变更项高亮；处理到 item-3 时其「优先级」与 tags 为无变化项，默认折叠；
-- 3) 采纳 item-1 后刷新，自动切换为 item-2；
-- 4) 任务全部处理后状态流转由后端接管，本脚本只造 proposals_ready 快照。
-- ============================================================
