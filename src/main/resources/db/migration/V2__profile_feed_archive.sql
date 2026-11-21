-- V2：个人资料（ID/头像/签名）、动态（feed）、档案
-- 注意：MySQL 8 与 H2 都要能跑，只用两边都认的语法。

ALTER TABLE users ADD COLUMN avatar_path VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN bio VARCHAR(200) NOT NULL DEFAULT '';

-- 「日常」改叫「动态」：先去掉旧约束，改完数据，再加新约束
-- 顺序不能反：MySQL 加 CHECK 时会校验已有行，而旧行里还有 'daily'
ALTER TABLE entries DROP CONSTRAINT ck_entries_kind;
UPDATE entries SET kind = 'feed' WHERE kind = 'daily';
ALTER TABLE entries ADD CONSTRAINT ck_entries_kind CHECK (kind IN ('study', 'feed', 'diary'));

-- 动态里的图片（一条动态最多九张）
CREATE TABLE IF NOT EXISTS entry_images (
  id       BIGINT       NOT NULL AUTO_INCREMENT,
  entry_id BIGINT       NOT NULL,
  path     VARCHAR(255) NOT NULL,
  width    INT          NOT NULL DEFAULT 0,
  height   INT          NOT NULL DEFAULT 0,
  sort     INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_entry_images_entry FOREIGN KEY (entry_id) REFERENCES entries (id) ON DELETE CASCADE
);
CREATE INDEX idx_entry_images_entry ON entry_images (entry_id, sort);

-- 档案：一个主题（一本书、一个游戏）
CREATE TABLE IF NOT EXISTS archives (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  title      VARCHAR(120) NOT NULL,
  summary    VARCHAR(300) NOT NULL DEFAULT '',
  created_at DATETIME     NOT NULL,
  updated_at DATETIME     NOT NULL,
  PRIMARY KEY (id)
);

-- 档案里的条目：一段剧情、一节笔记
CREATE TABLE IF NOT EXISTS archive_items (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  archive_id BIGINT       NOT NULL,
  title      VARCHAR(120) NOT NULL,
  body_md    TEXT         NOT NULL,
  sort       INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL,
  updated_at DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_archive_items_archive FOREIGN KEY (archive_id) REFERENCES archives (id) ON DELETE CASCADE
);
CREATE INDEX idx_archive_items_archive ON archive_items (archive_id, sort);
