-- V5：档案只留名字，简介字段不要了。
-- 档案本身不写正文，内容全在条目里。
ALTER TABLE archives DROP COLUMN summary;
