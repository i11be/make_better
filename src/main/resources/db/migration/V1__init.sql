-- V1：三张表。MySQL 8 和 H2（MySQL 模式）都能跑，所以别用 ENGINE / ENUM 这些 MySQL 专有写法。
-- 以后加字段：新建 V2__xxx.sql，不要改这个文件。

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  diary_hash    VARCHAR(100) NOT NULL,
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS entries (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  kind       VARCHAR(16)  NOT NULL,
  title      VARCHAR(120) NOT NULL,
  body_md    TEXT         NOT NULL,
  mood       VARCHAR(20)  NOT NULL DEFAULT '',
  tags       VARCHAR(200) NOT NULL DEFAULT '',
  entry_date DATE         NOT NULL,
  created_at DATETIME     NOT NULL,
  updated_at DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_entries_kind CHECK (kind IN ('study', 'daily', 'diary'))
);

CREATE INDEX idx_entries_kind_date ON entries (kind, entry_date);

CREATE TABLE IF NOT EXISTS settings (
  setting_key   VARCHAR(64) NOT NULL,
  setting_value TEXT        NOT NULL,
  PRIMARY KEY (setting_key)
);
