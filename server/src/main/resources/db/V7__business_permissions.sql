-- 业务侧权限（我的空间 + 项目 + 测试用例 + 评审 + 计划 + 缺陷）

-- 我的空间
INSERT INTO sys_permission (id, code, name, parent_code, module, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000001', 'ws-info',           '空间信息',   NULL,         '我的空间', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000002', 'ws-info:view',      '查看空间信息', 'ws-info',    '我的空间', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000003', 'ws-info:edit',      '编辑空间信息', 'ws-info',    '我的空间', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000004', 'ws-member',         '成员管理',   NULL,         '我的空间', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000005', 'ws-member:view',    '查看成员',   'ws-member',  '我的空间', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000006', 'ws-member:manage',  '管理成员',   'ws-member',  '我的空间', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000007', 'ws-invitation',     '邀请链接',   NULL,         '我的空间', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000008', 'ws-invitation:view','查看邀请链接', 'ws-invitation','我的空间', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000009', 'ws-invitation:manage','管理邀请链接','ws-invitation','我的空间', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

-- 项目
('c0000000-0000-0000-0000-000000000010', 'project',           '项目',      NULL,         '项目',    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000011', 'project:view',      '查看项目',    'project',    '项目',    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000012', 'project:create',    '创建项目',    'project',    '项目',    2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000013', 'project:edit',      '编辑项目',    'project',    '项目',    3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000014', 'project:delete',    '删除项目',    'project',    '项目',    4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000015', 'project:archive',   '归档/启封项目', 'project',    '项目',    5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000016', 'project:set-default','设为默认项目', 'project',    '项目',    6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

-- 测试用例
('c0000000-0000-0000-0000-000000000017', 'case',              '测试用例',    NULL,         '测试用例', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000018', 'case:view',         '查看用例',    'case',       '测试用例', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000019', 'case:edit',         '编辑用例',    'case',       '测试用例', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

-- 测试评审
('c0000000-0000-0000-0000-000000000020', 'review',            '测试评审',    NULL,         '测试评审', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000021', 'review:view',       '查看评审',    'review',     '测试评审', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000022', 'review:create',     '发起评审',    'review',     '测试评审', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000023', 'review:edit',       '评审操作',    'review',     '测试评审', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000024', 'review:complete',   '完成评审',    'review',     '测试评审', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

-- 测试计划
('c0000000-0000-0000-0000-000000000025', 'plan',              '测试计划',    NULL,         '测试计划', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000026', 'plan:view',         '查看计划',    'plan',       '测试计划', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000027', 'plan:create',       '创建计划',    'plan',       '测试计划', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000028', 'plan:execute',      '执行计划',    'plan',       '测试计划', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000029', 'plan:close',        '关闭计划',    'plan',       '测试计划', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),

-- 缺陷
('c0000000-0000-0000-0000-000000000030', 'bug',               '缺陷',       NULL,         '缺陷',    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000031', 'bug:view',          '查看缺陷',    'bug',        '缺陷',    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000032', 'bug:create',        '提交缺陷',    'bug',        '缺陷',    2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000033', 'bug:edit',          '编辑缺陷',    'bug',        '缺陷',    3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);
