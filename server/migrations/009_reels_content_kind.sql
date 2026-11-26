ALTER TABLE posts
  ADD COLUMN IF NOT EXISTS content_kind VARCHAR(16) NOT NULL DEFAULT 'post';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'posts_content_kind_check'
      AND conrelid = 'posts'::regclass
  ) THEN
    ALTER TABLE posts
      ADD CONSTRAINT posts_content_kind_check
      CHECK (content_kind IN ('post', 'reel'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS posts_content_kind_created_idx
  ON posts(content_kind, created_at DESC, id DESC);
