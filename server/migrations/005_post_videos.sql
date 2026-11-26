CREATE TABLE IF NOT EXISTS post_videos (
  post_id BIGINT PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
  mime_type VARCHAR(64) NOT NULL DEFAULT 'video/mp4',
  byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 52428800),
  data BYTEA NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS post_videos_created_idx ON post_videos(created_at DESC);
