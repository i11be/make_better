-- V3：学习笔记加「分类」：八股 / 算法，留空就是没分类。
-- 只加列，不删也不改已有行；老笔记自然归到「未分类」。
-- 分类值不写 CHECK 约束：以后还要加新分类，加约束就得每次写迁移，改在代码里校验。
ALTER TABLE entries ADD COLUMN category VARCHAR(32) NULL;
