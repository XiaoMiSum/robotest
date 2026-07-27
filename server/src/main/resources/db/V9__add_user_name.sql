-- 用户表增加姓名字段
ALTER TABLE sys_user ADD COLUMN name VARCHAR(50);

-- 已有用户使用用户名作为默认姓名
UPDATE sys_user SET name = username WHERE name IS NULL;

-- 设置为必填
ALTER TABLE sys_user ALTER COLUMN name SET NOT NULL;
