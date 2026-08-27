-- ============================================================
-- V1.5 — 内置函数种子数据
-- ============================================================
-- 以 Ryze 运行时注册的函数为准（ApiBuiltinFunctionRegistry），
-- 将内置函数元数据写入 api_function 表，供平台侧查询与审计。
-- scope = 'global'（公共函数全局可见），type = 'builtin'。
-- script 列为函数签名占位（实际执行由 Ryze 引擎接管）。
-- 幂等：uk_function_global(name) WHERE scope='global' AND is_deleted=false，
--        重复执行命中唯一约束即报错；生产环境应搭配 ON CONFLICT DO NOTHING。
--        此处使用 INSERT ... SELECT ... WHERE NOT EXISTS 保证幂等。

-- 内置函数 — 数据生成
INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000001'::uuid, 'global', NULL, NULL,
       'random', '生成指定区间的随机整数', 'min:区间下界, max:区间上界',
       '${__random(min, max)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'random' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000002'::uuid, 'global', NULL, NULL,
       'random_string', '生成指定长度的随机字符串（含字母与数字）', 'length:字符串长度',
       '${__random_string(length)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'random_string' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000003'::uuid, 'global', NULL, NULL,
       'faker', '按 Faker 表达式生成仿真测试数据', 'path:Faker数据路径(如name.fullName), locale:语言区域(默认zh_CN)',
       '${__faker(path[, locale])}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'faker' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000004'::uuid, 'global', NULL, NULL,
       'uuid', '生成随机 UUID（去连字符）', '',
       '${__uuid()}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'uuid' AND scope = 'global' AND is_deleted = FALSE);

-- 内置函数 — 日期时间
INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000005'::uuid, 'global', NULL, NULL,
       'timestamp', '当前时间戳，默认毫秒；可指定秒或日期格式', 'format|_s:日期格式串(传_s返回秒级时间戳)',
       '${__timestamp([format|_s])}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'timestamp' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000006'::uuid, 'global', NULL, NULL,
       'time_shift', '基于当前时间按 ISO-8601 偏移量平移后格式化输出', 'format:输出日期格式(默认yyyy-MM-dd HH:mm:ss), offset:ISO-8601偏移量(如+1d)',
       '${__time_shift([format,] offset)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'time_shift' AND scope = 'global' AND is_deleted = FALSE);

-- 内置函数 — 数据处理
INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000007'::uuid, 'global', NULL, NULL,
       'json', '将多组 k=v 参数组装为 JSON 字符串', 'k=v:键值参数(至少一组)',
       '${__json(k1=v1, k2=v2)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'json' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000008'::uuid, 'global', NULL, NULL,
       'json_read', '从 JSON 文本中按 JsonPath 提取值', 'json:JSON文本或变量引用, jsonpath:JsonPath表达式',
       '${__json_read(json, jsonpath)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'json_read' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000009'::uuid, 'global', NULL, NULL,
       'url_encode', 'URL 编码（application/x-www-form-urlencoded）', 'content:待编码文本',
       '${__url_encode(content)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'url_encode' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000010'::uuid, 'global', NULL, NULL,
       'url_decode', 'URL 解码', 'content:待解码文本',
       '${__url_decode(content)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'url_decode' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000011'::uuid, 'global', NULL, NULL,
       'base64_encode', 'Base64 编码', 'content:待编码文本',
       '${__base64_encode(content)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'base64_encode' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000012'::uuid, 'global', NULL, NULL,
       'base64_decode', 'Base64 解码', 'content:待解码Base64文本',
       '${__base64_decode(content)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'base64_decode' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000013'::uuid, 'global', NULL, NULL,
       'property', '读取平台变量值（就近作用域解析）', 'key:变量名',
       '${__property(key)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'property' AND scope = 'global' AND is_deleted = FALSE);

-- 内置函数 — 安全加密
INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000014'::uuid, 'global', NULL, NULL,
       'digest', '摘要算法（md5/sha-1/sha-256 等，支持盐值）', 'algorithm:摘要算法名, content:原文, salt:盐值(可选)',
       '${__digest(algorithm, content[, salt])}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'digest' AND scope = 'global' AND is_deleted = FALSE);

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc, script, type, enabled, updated_by, is_deleted, created_at, updated_at)
SELECT 'f0000000-0000-0000-0000-000000000015'::uuid, 'global', NULL, NULL,
       'google2fa', '根据 2FA 密钥生成 Google 验证码', 'secretKey:Google Authenticator 共享密钥',
       '${__google2fa(secretKey)}', 'builtin', TRUE,
       '00000000-0000-0000-0000-000000000000'::uuid, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM api_function WHERE name = 'google2fa' AND scope = 'global' AND is_deleted = FALSE);
