package com.makemebetter.web;

import com.makemebetter.service.DiaryGate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** 日记区：登录之后还要再解一次锁。 */
@Component
public class DiaryGateInterceptor implements HandlerInterceptor {

    private final DiaryGate diaryGate;

    public DiaryGateInterceptor(DiaryGate diaryGate) {
        this.diaryGate = diaryGate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (diaryGate.isUnlocked(request.getSession(false))) {
            return true;
        }
        response.sendRedirect("/diary/unlock");
        return false;
    }
}
