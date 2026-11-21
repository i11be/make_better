package com.makemebetter.web;

import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.User;
import com.makemebetter.repository.UserRepository;
import com.makemebetter.service.DiaryGate;
import com.makemebetter.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 每个页面都要用到的东西：站点文案、导航高亮、登录状态、日记锁状态、头像与 ID。 */
@ControllerAdvice(basePackages = "com.makemebetter.web")
public class GlobalModelAttributes {

    private final SettingService settings;
    private final DiaryGate diaryGate;
    private final UserRepository users;

    public GlobalModelAttributes(SettingService settings, DiaryGate diaryGate, UserRepository users) {
        this.settings = settings;
        this.diaryGate = diaryGate;
        this.users = users;
    }

    @ModelAttribute
    public void populate(Model model, HttpServletRequest request, Authentication authentication) {
        model.addAttribute("site", settings.all());
        model.addAttribute("profile", users.findFirstByOrderByIdAsc().orElse(null));
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("kinds", EntryKind.values());
        model.addAttribute("user", loggedInName(authentication));
        model.addAttribute("diaryUnlocked", diaryGate.isUnlocked(request.getSession(false)));
        model.addAttribute("diaryMinutes", diaryGate.minutes());
        model.addAttribute("year", java.time.LocalDate.now().getYear());
        model.addAttribute("kStudy", EntryKind.STUDY);
        model.addAttribute("kFeed", EntryKind.FEED);
        model.addAttribute("kDiary", EntryKind.DIARY);
    }

    @ModelAttribute("initial")
    public String initial() {
        return users.findFirstByOrderByIdAsc()
                .map(User::getUsername)
                .filter(name -> !name.isBlank())
                .map(name -> name.substring(0, 1).toUpperCase())
                .orElse("M");
    }

    private String loggedInName(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.isAuthenticated() ? authentication.getName() : null;
    }
}
