-- Maintain denormalized like counts so feed reads and popular sorting
-- do not execute a COUNT(*) subquery for every post.
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS likes_count BIGINT NOT NULL DEFAULT 0;

UPDATE posts AS p
SET likes_count = (
    SELECT COUNT(*)
    FROM post_likes AS l
    WHERE l.post_id = p.id
);

CREATE OR REPLACE FUNCTION vibecodegram_sync_post_likes_count()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE posts SET likes_count = likes_count + 1 WHERE id = NEW.post_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE posts SET likes_count = GREATEST(likes_count - 1, 0) WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS post_likes_count_sync ON post_likes;
CREATE TRIGGER post_likes_count_sync
AFTER INSERT OR DELETE ON post_likes
FOR EACH ROW
EXECUTE FUNCTION vibecodegram_sync_post_likes_count();

CREATE INDEX IF NOT EXISTS posts_popular_idx
    ON posts (likes_count DESC, created_at DESC, id DESC);
