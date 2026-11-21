package com.makemebetter.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 日记那道锁：解锁后在 session 里记一个时间戳，超过时限自动重新上锁。
 * 站点登录是 Spring Security 管的，这里是第二层。
 */
@Component
public class DiaryGate {

    private static final String SESSION_KEY = "diaryUnlockedAt";

    private final Duration ttl;

    public DiaryGate(@Value("${app.diary-unlock-minutes:30}") long minutes) {
        this.ttl = Duration.ofMinutes(minutes);
    }

    public boolean isUnlocked(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object value = session.getAttribute(SESSION_KEY);
        if (!(value instanceof Instant unlockedAt)) {
            return false;
        }
        if (Instant.now().isAfter(unlockedAt.plus(ttl))) {
            session.removeAttribute(SESSION_KEY);
            return false;
        }
        return true;
    }

    public void unlock(HttpSession session) {
        session.setAttribute(SESSION_KEY, Instant.now());
    }

    public void lock(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }

    public long minutes() {
        return ttl.toMinutes();
    }
}
