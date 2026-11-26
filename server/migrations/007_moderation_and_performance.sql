-- Additive moderation controls and indexes for the existing production schema.
-- This migration intentionally does not rewrite or delete user content.

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS user_blocks_blocked_idx
    ON user_blocks (blocked_id, blocker_id);

CREATE TABLE IF NOT EXISTS post_reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    reason VARCHAR(32) NOT NULL CHECK (reason IN ('spam', 'harassment', 'violence', 'misinformation', 'copyright', 'other')),
    details VARCHAR(1000) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'reviewed', 'actioned', 'dismissed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (reporter_id, post_id)
);

CREATE INDEX IF NOT EXISTS post_reports_queue_idx
    ON post_reports (status, created_at DESC);

-- Cover reverse lookups and common per-user feed filters.
CREATE INDEX IF NOT EXISTS post_likes_user_idx
    ON post_likes (user_id, post_id);

CREATE INDEX IF NOT EXISTS saved_posts_user_idx
    ON saved_posts (user_id, created_at DESC, post_id);

CREATE INDEX IF NOT EXISTS hidden_posts_user_idx
    ON hidden_posts (user_id, post_id);

CREATE INDEX IF NOT EXISTS posts_author_created_idx
    ON posts (author_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS story_views_viewer_idx
    ON story_views (viewer_id, story_id);

CREATE INDEX IF NOT EXISTS stories_author_created_idx
    ON stories (author_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS notifications_unread_idx
    ON notifications (recipient_id, id DESC)
    WHERE read_at IS NULL;
