package com.makemebetter.service;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.StudyCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习列表的筛选、分组。
 * 就自己一个人用，条目不会上万，所以统一在内存里过滤，不写一堆查询方法。
 */
@Service
@Transactional(readOnly = true)
public class StudyService {

    /** 分类页签里「没分类」那一档的 code */
    public static final String UNCATEGORIZED = "none";
    /** 没打标签的笔记归到这一组 */
    public static final String UNTAGGED = "未打标签";

    private final EntryService entries;

    public StudyService(EntryService entries) {
        this.entries = entries;
    }

    /**
     * @param catParam  "" 全部 / "bagu" / "algo" / "none" 未分类
     * @param sortParam "time" 按时间 / "tag" 按标签分组
     * @param tagParam  只看某个标签，空表示不筛
     */
    public StudyPage page(String catParam, String sortParam, String tagParam) {
        List<Entry> all = entries.list(EntryKind.STUDY);
        String cat = normalizeCat(catParam);
        String tag = tagParam == null ? "" : tagParam.strip();
        String sort = "tag".equals(sortParam) ? "tag" : "time";

        List<Entry> items = new ArrayList<>();
        for (Entry entry : all) {
            if (!inCategory(entry, cat)) {
                continue;
            }
            if (!tag.isEmpty() && !tag.equalsIgnoreCase(tagOf(entry))) {
                continue;
            }
            items.add(entry);
        }

        List<StudyPage.TagGroup> groups = "tag".equals(sort) ? group(items) : List.of();
        return new StudyPage(List.copyOf(items), groups, tabs(all), cat, sort, tag);
    }

    private boolean inCategory(Entry entry, String cat) {
        if (cat.isEmpty()) {
            return true;
        }
        String code = codeOf(entry);
        if (UNCATEGORIZED.equals(cat)) {
            return code.isEmpty();
        }
        return cat.equals(code);
    }

    private String codeOf(Entry entry) {
        return entry.getCategory() == null ? "" : entry.getCategory();
    }

    private String tagOf(Entry entry) {
        return entry.getTags() == null ? "" : entry.getTags().strip();
    }

    private String normalizeCat(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (UNCATEGORIZED.equals(raw.strip())) {
            return UNCATEGORIZED;
        }
        StudyCategory category = StudyCategory.fromCode(raw.strip());
        return category == null ? "" : category.getCode();
    }

    /** 按标签分组：篇数多的在前，没打标签的永远垫底 */
    private List<StudyPage.TagGroup> group(List<Entry> items) {
        Map<String, List<Entry>> byTag = new LinkedHashMap<>();
        for (Entry entry : items) {
            String tag = tagOf(entry);
            byTag.computeIfAbsent(tag.isEmpty() ? UNTAGGED : tag, key -> new ArrayList<>()).add(entry);
        }
        List<StudyPage.TagGroup> groups = new ArrayList<>();
        byTag.forEach((label, list) -> groups.add(new StudyPage.TagGroup(label, List.copyOf(list))));
        groups.sort((a, b) -> {
            if (a.label().equals(UNTAGGED) != b.label().equals(UNTAGGED)) {
                return a.label().equals(UNTAGGED) ? 1 : -1;
            }
            int byCount = Integer.compare(b.items().size(), a.items().size());
            return byCount != 0 ? byCount : a.label().compareToIgnoreCase(b.label());
        });
        return groups;
    }

    /** 全部 + 每个分类 + 未分类，加一个分类这里自动多一档 */
    private List<StudyPage.CategoryTab> tabs(List<Entry> all) {
        List<StudyPage.CategoryTab> tabs = new ArrayList<>();
        tabs.add(new StudyPage.CategoryTab("", "全部", all.size()));
        for (StudyCategory category : StudyCategory.values()) {
            long count = all.stream().filter(e -> category.getCode().equals(codeOf(e))).count();
            tabs.add(new StudyPage.CategoryTab(category.getCode(), category.getLabel(), count));
        }
        long none = all.stream().filter(e -> codeOf(e).isEmpty()).count();
        tabs.add(new StudyPage.CategoryTab(UNCATEGORIZED, "未分类", none));
        return tabs;
    }
}
