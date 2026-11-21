package com.makemebetter.web;

import com.makemebetter.domain.User;
import com.makemebetter.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.io.IOException;

/** 还没有账号时，所有页面都先去 /setup。 */
@Component
public class SetupGateInterceptor implements HandlerInterceptor {

    private final UserRepository users;

    public SetupGateInterceptor(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (handler instanceof ResourceHttpRequestHandler) {
            return true;
        }
        if (users.findFirstByOrderByIdAsc().isPresent()) {
            return true;
        }
        response.sendRedirect("/setup");
        return false;
    }
}
