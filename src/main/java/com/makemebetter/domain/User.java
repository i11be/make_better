package com.makemebetter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 全站只有一行：这是「我自己」的账号。username 就是主页上显示的 ID。 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 主页上显示的 ID */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt 哈希 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 日记那道锁的 BCrypt 哈希 */
    @Column(name = "diary_hash", nullable = false, length = 100)
    private String diaryHash;

    /** 头像在 upload 目录下的相对路径，空串表示还没传 */
    @Column(name = "avatar_path", nullable = false, length = 255)
    private String avatarPath = "";

    /** 一句签名 */
    @Column(nullable = false, length = 200)
    private String bio = "";

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;

    protected User() {
    }

    public User(String username, String passwordHash, String diaryHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.diaryHash = diaryHash;
        this.createdAt = java.time.LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDiaryHash() {
        return diaryHash;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    /** 页面直接用的头像地址；没传过头像就是空串 */
    public String getAvatarUrl() {
        return avatarPath == null || avatarPath.isBlank() ? "" : "/files/" + avatarPath;
    }

    public String getBio() {
        return bio;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeDiaryPassword(String diaryHash) {
        this.diaryHash = diaryHash;
    }

    public void updateProfile(String username, String bio, String avatarPath) {
        if (username != null && !username.isBlank()) {
            this.username = username.strip();
        }
        this.bio = bio == null ? "" : bio.strip();
        if (avatarPath != null) {
            this.avatarPath = avatarPath;
        }
    }
}
