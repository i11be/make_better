package com.makemebetter.service;

import com.makemebetter.domain.SessionStatus;
import com.makemebetter.domain.StudySession;
import com.makemebetter.repository.StudySessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 学习计时：一次一条记录，页面刷新/关掉都靠时间戳恢复。
 * 统计口径：只算跑完的（DONE），计划多少就记多少；中途放弃（CANCELLED）不算。
 */
@Service
@Transactional(readOnly = true)
public class StudyTimerService {

    /** 四段时长，单位秒 */
    public record Stats(long today, long week, long month, long total) {
    }

    private final StudySessionRepository sessions;

    public StudyTimerService(StudySessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * 把「时间已经过了但还挂着 running」的会话补记成跑完。
     * 页面关掉过、或者电脑睡了一觉，回来时靠这个对齐数据。
     */
    @Transactional
    public int settleExpired() {
        LocalDateTime now = LocalDateTime.now();
        int settled = 0;
        for (StudySession session : sessions.findByStatus(SessionStatus.RUNNING)) {
            if (!now.isBefore(session.plannedEnd())) {
                session.markDone(session.plannedEnd());
                sessions.save(session);
                settled++;
            }
        }
        return settled;
    }

    public Optional<StudySession> current() {
        return sessions.findFirstByStatusOrderByIdDesc(SessionStatus.RUNNING);
    }

    /** 剩余秒数（不足 0 按 0 算） */
    public long remainingSeconds(StudySession session) {
        long left = ChronoUnit.SECONDS.between(LocalDateTime.now(), session.plannedEnd());
        return Math.max(0, left);
    }

    @Transactional
    public StudySession start(int minutes) {
        settleExpired();
        Optional<StudySession> running = current();
        if (running.isPresent()) {
            return running.get();
        }
        return sessions.save(new StudySession(LocalDateTime.now(), minutes));
    }

    /** 时间到：正常收尾。重复调用无副作用 */
    @Transactional
    public void finish(Long id) {
        sessions.findById(id)
                .filter(s -> s.getStatus() == SessionStatus.RUNNING)
                .ifPresent(s -> {
                    // 用“真正结束的那一刻”记账，别写成计划结束时间（那可能在未来）
                    s.markDone(LocalDateTime.now());
                    sessions.save(s);
                });
    }

    /** 放弃：不计时长 */
    @Transactional
    public void cancel(Long id) {
        sessions.findById(id)
                .filter(s -> s.getStatus() == SessionStatus.RUNNING)
                .ifPresent(s -> {
                    s.markCancelled(LocalDateTime.now());
                    sessions.save(s);
                });
    }

    public Stats stats() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);

        long todaySeconds = 0;
        long weekSeconds = 0;
        long monthSeconds = 0;
        long totalSeconds = 0;

        for (StudySession session : sessions.findByStatus(SessionStatus.DONE)) {
            if (session.getEndedAt() == null) {
                continue;
            }
            long seconds = session.plannedSeconds();
            LocalDate day = session.getEndedAt().toLocalDate();
            totalSeconds += seconds;
            if (!day.isBefore(monthStart)) {
                monthSeconds += seconds;
            }
            if (!day.isBefore(weekStart)) {
                weekSeconds += seconds;
            }
            if (day.equals(today)) {
                todaySeconds += seconds;
            }
        }
        return new Stats(todaySeconds, weekSeconds, monthSeconds, totalSeconds);
    }
}
