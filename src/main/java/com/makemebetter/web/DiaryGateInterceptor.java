package com.makemebetter.web;

import com.makemebetter.service.DiaryGate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 日记区的一道锁，两条规则：
 * <ol>
 *   <li>进 /diary/** 没解锁 → 跳去输密码</li>
 *   <li>在解锁状态下打开了别的页面（GET）→ 立刻锁回去</li>
 * </ol>
 * 第二条是「离开就锁」：从日记回到首页或别的栏目之后，下次再进日记要重新输密码。
 * 只对 GET 生效，是因为 POST 都是日记自己的保存/删除，不能算离开。
 */
@Component
public class DiaryGateInterceptor implements HandlerInterceptor {

    private final DiaryGate diaryGate;

    public DiaryGateInterceptor(DiaryGate diaryGate) {
        this.diaryGate = diaryGate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        boolean inDiary = "/diary".equals(path) || path.startsWith("/diary/");

        if (inDiary) {
            if (diaryGate.isUnlocked(request.getSession(false))) {
                return true;
            }
            response.sendRedirect("/diary/unlock");
            return false;
        }

        // 不在日记区：只要看过别的页面，就把锁挂回去
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            diaryGate.lock(request.getSession(false));
        }
        return true;
    }
}
