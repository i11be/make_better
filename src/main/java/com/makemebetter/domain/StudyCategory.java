package com.makemebetter.domain;

/**
 * 学习笔记的分类。现在只有两门，加一个只需要在这里加一行，页面会自动多一个选项。
 * code 存进数据库，label 是给人看的；不认识的 code 一律当成「没分类」。
 */
public enum StudyCategory {

    BAGU("bagu", "八股"),
    ALGO("algo", "算法");

    private final String code;
    private final String label;

    StudyCategory(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 空值或不认识的 code 返回 null —— 当没分类处理，不留脏数据 */
    public static StudyCategory fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (StudyCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }

    /** 给模板用：code → 中文，认不出来就返回空串 */
    public static String labelOf(String code) {
        StudyCategory category = fromCode(code);
        return category == null ? "" : category.label;
    }
}
