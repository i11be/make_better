-- V4：学习笔记的正文不再受 64KB 的 TEXT 限制。
-- MEDIUMTEXT 上限 16MB，写到天荒地老也用不完；只放宽，不动数据。
ALTER TABLE entries MODIFY body_md MEDIUMTEXT NOT NULL;
