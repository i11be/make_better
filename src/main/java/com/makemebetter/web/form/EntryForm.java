package com.makemebetter.web.form;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 后台写作用的一张表单：学习 / 日常 / 日记都用它。 */
public class EntryForm {

    private Long id;
    private String kind = "study";
    private String title = "";
    private String bodyMd = "";
    private String mood = "";
    private String tags = "";

    /** 学习的分类，其它类型留空 */
    private String category = "";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate entryDate = LocalDate.now();

    public static EntryForm forKind(String kind) {
        EntryForm form = new EntryForm();
        form.kind = kind == null || kind.isBlank() ? "study" : kind;
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBodyMd() {
        return bodyMd;
    }

    public void setBodyMd(String bodyMd) {
        this.bodyMd = bodyMd;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }
}
