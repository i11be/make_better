package com.makemebetter.domain;

/**
 * 四条主线：学习、动态、日记、档案（档案有自己的表，不在这里）。
 * code 是存进数据库的值，其余是给人看的文案。
 */
public enum EntryKind {

    STUDY("study", "学习", "/study", "今天学到的东西，写下来才算真的学到"),
    FEED("feed", "动态", "/feed", "生活里的小片段，几张图也算"),
    DIARY("diary", "日记", "/diary", "只有你能看见的部分");

    private final String code;
    private final String label;
    private final String path;
    private final String hint;

    EntryKind(String code, String label, String path, String hint) {
        this.code = code;
        this.label = label;
        this.path = path;
        this.hint = hint;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getPath() {
        return path;
    }

    public String getHint() {
        return hint;
    }

    public static EntryKind fromCode(String code) {
        if ("daily".equals(code)) {
            return FEED;
        }
        for (EntryKind kind : values()) {
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        return STUDY;
    }
}
