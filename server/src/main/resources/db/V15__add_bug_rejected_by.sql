-- 记录执行拒绝的人：重开已拒绝缺陷时处理人回设为拒绝人（存量数据保持 NULL，重开时处理人不变）
ALTER TABLE bug ADD COLUMN rejected_by UUID NULL;
