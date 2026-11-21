package com.makemebetter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 档案里的一条：一段剧情、一节笔记。 */
@Entity
@Table(name = "archive_items")
public class ArchiveItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archive_id", nullable = false)
    private Long archiveId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "body_md", nullable = false, columnDefinition = "TEXT")
    private String bodyMd;

    @Column(nullable = false)
    private int sort;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ArchiveItem() {
    }

    public ArchiveItem(Long archiveId, String title, String bodyMd, int sort) {
        this.archiveId = archiveId;
        this.title = title;
        this.bodyMd = bodyMd;
        this.sort = sort;
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

    public void update(String title, String bodyMd) {
        this.title = title;
        this.bodyMd = bodyMd;
    }

    public void moveTo(int sort) {
        this.sort = sort;
    }

    public Long getId() {
        return id;
    }

    public Long getArchiveId() {
        return archiveId;
    }

    public String getTitle() {
        return title;
    }

    public String getBodyMd() {
        return bodyMd;
    }

    public int getSort() {
        return sort;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
