-- ============================================================
-- V1.6 — 场景关注关系表
-- ============================================================
-- 场景关注（follow/unfollow）机制，与 api_interface_follow 同构。
-- 幂等：IF NOT EXISTS 保证重复执行安全。

CREATE TABLE IF NOT EXISTS api_scene_follow (
    id            UUID      PRIMARY KEY,
    scene_id      UUID      NOT NULL,
    user_id       UUID      NOT NULL,
    is_deleted    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_scene_follow ON api_scene_follow(scene_id, user_id) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_sfollow_scene ON api_scene_follow(scene_id);
CREATE INDEX IF NOT EXISTS idx_sfollow_user ON api_scene_follow(user_id);

COMMENT ON TABLE api_scene_follow IS '场景关注表（按用户记录，取消关注即逻辑删除）';
COMMENT ON COLUMN api_scene_follow.scene_id IS '关联场景 ID';
COMMENT ON COLUMN api_scene_follow.user_id IS '关注用户 ID';
