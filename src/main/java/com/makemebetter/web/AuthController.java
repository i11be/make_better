package com.makemebetter.web;

import com.makemebetter.domain.User;
import com.makemebetter.repository.UserRepository;
import com.makemebetter.service.EntryService;
import com.makemebetter.web.form.SetupForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.regex.Pattern;

@Controller
public class AuthController {

    private static final Pattern USERNAME = Pattern.compile("^[\\w.@\\u4e00-\\u9fa5-]{2,24}$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EntryService entries;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, EntryService entries) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.entries = entries;
    }

    @GetMapping("/login")
    public String login(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("title", "登录");
        return "login";
    }

    @GetMapping("/setup")
    public String setupForm(Model model) {
        if (users.findFirstByOrderByIdAsc().isPresent()) {
            return "redirect:/login";
        }
        model.addAttribute("title", "先设置一下");
        model.addAttribute("form", new SetupForm());
        return "setup";
    }

    @PostMapping("/setup")
    public String setup(@ModelAttribute("form") SetupForm form, Model model,
                        HttpServletRequest request, HttpServletResponse response) {
        if (users.findFirstByOrderByIdAsc().isPresent()) {
            return "redirect:/login";
        }

        String username = form.getUsername() == null ? "" : form.getUsername().strip();
        String error = validate(username, form);
        if (error != null) {
            model.addAttribute("title", "先设置一下");
            model.addAttribute("error", error);
            return "setup";
        }

        users.save(new User(username,
                passwordEncoder.encode(form.getPassword()),
                passwordEncoder.encode(form.getDiary())));
        entries.seedWelcomeIfEmpty();
        loginNow(username, request, response);
        return "redirect:/?ok=welcome";
    }

    private String validate(String username, SetupForm form) {
        if (!USERNAME.matcher(username).matches()) {
            return "用户名 2–24 个字，只能是中文、字母、数字、下划线、点、@ 或短横线。";
        }
        String password = form.getPassword() == null ? "" : form.getPassword();
        String diary = form.getDiary() == null ? "" : form.getDiary();
        String diary2 = form.getDiary2() == null ? "" : form.getDiary2();
        if (password.length() < 8) {
            return "登录密码至少 8 位。";
        }
        if (diary.length() < 4) {
            return "日记密码至少 4 位。";
        }
        if (!diary.equals(diary2)) {
            return "两次输入的日记密码不一致。";
        }
        return null;
    }

    /** 建完账号直接登录，别让人再输一遍。 */
    private void loginNow(String username, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                username, "N/A", List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.isAuthenticated();
    }
}
