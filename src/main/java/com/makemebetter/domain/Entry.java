package com.makemebetter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一条内容：学习笔记 / 日常 / 日记。 */
@Entity
@Table(name = "entries")
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EntryKindConverter.class)
    @Column(nullable = false, length = 16)
    private EntryKind kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "body_md", nullable = false, columnDefinition = "TEXT")
    private String bodyMd;

    @Column(nullable = false, length = 20)
    private String mood;

    @Column(nullable = false, length = 200)
    private String tags;

    /** 学习的分类（StudyCategory 的 code），其它类型为空 */
    @Column(length = 32)
    private String category;

    /** 事情发生的那天，不是录入时间 */
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Entry() {
    }

    public Entry(EntryKind kind, String title, String bodyMd, String mood, String tags, String category,
                 LocalDate entryDate) {
        this.kind = kind;
        this.title = title;
        this.bodyMd = bodyMd;
        this.mood = mood;
        this.tags = tags;
        this.category = category;
        this.entryDate = entryDate;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void update(EntryKind kind, String title, String bodyMd, String mood, String tags, String category,
                       LocalDate entryDate) {
        this.kind = kind;
        this.title = title;
        this.bodyMd = bodyMd;
        this.mood = mood;
        this.tags = tags;
        this.category = category;
        this.entryDate = entryDate;
    }

    public Long getId() {
        return id;
    }

    public EntryKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getBodyMd() {
        return bodyMd;
    }

    public String getMood() {
        return mood;
    }

    public String getTags() {
        return tags;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
