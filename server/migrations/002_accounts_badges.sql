ALTER TABLE users ADD COLUMN IF NOT EXISTS is_premium BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS badges (
  code VARCHAR(32) PRIMARY KEY,
  title VARCHAR(64) NOT NULL,
  description VARCHAR(240) NOT NULL,
  color VARCHAR(16) NOT NULL,
  icon VARCHAR(16) NOT NULL,
  premium_only BOOLEAN NOT NULL DEFAULT FALSE,
  staff_only BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO badges(code,title,description,color,icon,premium_only,staff_only) VALUES
 ('premium','Premium','Активная подписка vibecodeGram','#D6A8FF','✦',TRUE,FALSE),
 ('premium_plus','Premium Plus','Расширенный уровень подписки','#55C8FF','✧',TRUE,FALSE),
 ('premium_elite','Premium Elite','Особый уровень подписки','#F0C76A','♛',TRUE,FALSE),
 ('founder','Founder','Основатель проекта','#F2C14E','♛',FALSE,TRUE),
 ('developer','Developer','Разработчик проекта','#FF7777','◆',FALSE,TRUE),
 ('co_developer','Co-Developer','Соразработчик проекта','#75B8FF','⬡',FALSE,TRUE),
 ('architect','Architect','Архитектор проекта','#B69AFF','✧',FALSE,TRUE),
 ('team','Team','Участник команды','#65D6B0','⬢',FALSE,TRUE)
ON CONFLICT(code) DO UPDATE SET title=EXCLUDED.title,description=EXCLUDED.description,color=EXCLUDED.color,icon=EXCLUDED.icon,premium_only=EXCLUDED.premium_only,staff_only=EXCLUDED.staff_only;
CREATE TABLE IF NOT EXISTS user_badges (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  badge_code VARCHAR(32) NOT NULL REFERENCES badges(code),
  granted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(user_id,badge_code)
);
CREATE TABLE IF NOT EXISTS badge_audit (
  id BIGSERIAL PRIMARY KEY,
  target_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  badge_code VARCHAR(32) NOT NULL REFERENCES badges(code),
  actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  action VARCHAR(12) NOT NULL CHECK(action IN ('grant','revoke')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS device_accounts (
  device_id VARCHAR(128) NOT NULL,
  account_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(device_id,account_id)
);
CREATE INDEX IF NOT EXISTS device_accounts_account_idx ON device_accounts(account_id);

ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_tier VARCHAR(16) NOT NULL DEFAULT 'none' CHECK (premium_tier IN ('none','premium','premium_plus','premium_elite'));
