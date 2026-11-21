package com.makemebetter.service;

import com.makemebetter.domain.Entry;

import java.util.List;

/** 学习列表页要的全部东西：筛过的条目、标签分组、分类页签。 */
public record StudyPage(
        List<Entry> items,
        List<TagGroup> groups,
        List<CategoryTab> tabs,
        String catCode,
        String sortCode,
        String tag) {

    public boolean byTag() {
        return "tag".equals(sortCode);
    }

    public boolean filtered() {
        return !catCode.isEmpty() || !tag.isEmpty();
    }

    /** 一组同标签的笔记 */
    public record TagGroup(String label, List<Entry> items) {
    }

    /** 分类页签：code 是 URL 上用的值，空串表示「全部」 */
    public record CategoryTab(String code, String label, long count) {
    }
}
