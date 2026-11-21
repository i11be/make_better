package com.makemebetter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 动态里的一张图。path 是 upload 目录下的相对路径。 */
@Entity
@Table(name = "entry_images")
public class EntryImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false)
    private Long entryId;

    @Column(nullable = false, length = 255)
    private String path;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private int sort;

    protected EntryImage() {
    }

    public EntryImage(Long entryId, String path, int width, int height, int sort) {
        this.entryId = entryId;
        this.path = path;
        this.width = width;
        this.height = height;
        this.sort = sort;
    }

    public Long getId() {
        return id;
    }

    public Long getEntryId() {
        return entryId;
    }

    public String getPath() {
        return path;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSort() {
        return sort;
    }
}
