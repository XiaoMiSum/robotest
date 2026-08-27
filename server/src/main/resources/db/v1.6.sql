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
