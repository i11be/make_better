package com.makemebetter.service;

import com.makemebetter.domain.StudyCategory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 模板里用的小工具：日期、摘要、标签。Thymeleaf 里用 {@code @viewHelper.xxx(...)} 调。 */
@Component("viewHelper")
public class ViewHelper {

    private static final String[] WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    public String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    public String weekday(LocalDate value) {
        if (value == null) {
            return "";
        }
        return "星期" + WEEKDAYS[value.getDayOfWeek().getValue() - 1];
    }

    public String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    /** 秒 → 「1 小时 25 分」，统计栏用 */
    public String duration(long seconds) {
        long minutes = Math.round(seconds / 60.0);
        if (minutes <= 0) {
            return "0 分";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " 分";
        }
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分";
    }

    /** 秒 → 电子钟字符串：25:00 / 1:05:00（没有 JS 时的兜底） */
    public String clock(long seconds) {
        long left = Math.max(0, seconds);
        long hours = left / 3600;
        long minutes = (left % 3600) / 60;
        long rest = left % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, rest);
        }
        return String.format("%02d:%02d", minutes, rest);
    }

    /** Markdown → 纯文本摘要 */
    public String excerpt(String markdown, int max) {
        if (markdown == null) {
            return "";
        }
        String text = markdown
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("[*_`>~-]", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    public List<String> tags(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,，\\s]+")) {
            String tag = part.strip();
            if (!tag.isEmpty()) {
                out.add(tag);
            }
        }
        return out;
    }

    /** 把表单里的标签规整成 "a, b, c" */
    public String normalizeTags(String raw) {
        return String.join(", ", tags(raw));
    }

    /** 学习的分类：code → 中文，认不出来返回空串 */
    public String categoryLabel(String code) {
        return StudyCategory.labelOf(code);
    }

    /** 动态配图的缩略图路径（约定：同名 + _s.jpg） */
    public String thumb(String path) {
        return FileStorage.thumbOf(path);
    }
}
