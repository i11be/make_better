-- V6：学习计时（番茄钟）的会话记录，主页四段统计都从这里算
CREATE TABLE IF NOT EXISTS study_sessions (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  started_at      DATETIME    NOT NULL,
  planned_minutes INT         NOT NULL,
  ended_at        DATETIME    NULL,
  status          VARCHAR(16) NOT NULL
);

CREATE INDEX idx_study_sessions_status ON study_sessions (status);
CREATE INDEX idx_study_sessions_ended ON study_sessions (ended_at);
