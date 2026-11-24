package com.makemebetter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 一次学习计时：开始写一条，跑完或放弃时收尾。主页的今日/本周/本月/累计都从这张表算。 */
@Entity
@Table(name = "study_sessions")
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status;

    protected StudySession() {
    }

    public StudySession(LocalDateTime startedAt, int plannedMinutes) {
        this.startedAt = startedAt;
        this.plannedMinutes = plannedMinutes;
        this.status = SessionStatus.RUNNING;
    }

    /** 按计划时间收尾（正常跑完，或者页面不在了、事后补记） */
    public void markDone(LocalDateTime endedAt) {
        this.endedAt = endedAt;
        this.status = SessionStatus.DONE;
    }

    /** 中途放弃：不记时长 */
    public void markCancelled(LocalDateTime endedAt) {
        this.endedAt = endedAt;
        this.status = SessionStatus.CANCELLED;
    }

    /** 计划结束的时刻 */
    public LocalDateTime plannedEnd() {
        return startedAt.plusMinutes(plannedMinutes);
    }

    public long plannedSeconds() {
        return plannedMinutes * 60L;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public SessionStatus getStatus() {
        return status;
    }
}
