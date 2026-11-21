package com.makemebetter.web;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.User;
import com.makemebetter.repository.UserRepository;
import com.makemebetter.service.DiaryGate;
import com.makemebetter.service.EntryService;
import com.makemebetter.service.MarkdownService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/diary")
public class DiaryController {

    private final EntryService entries;
    private final MarkdownService markdown;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DiaryGate diaryGate;

    public DiaryController(EntryService entries, MarkdownService markdown, UserRepository users,
                           PasswordEncoder passwordEncoder, DiaryGate diaryGate) {
        this.entries = entries;
        this.markdown = markdown;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.diaryGate = diaryGate;
    }

    @GetMapping("/unlock")
    public String lockPage(HttpServletRequest request, Model model) {
        if (diaryGate.isUnlocked(request.getSession(false))) {
            return "redirect:/diary";
        }
        model.addAttribute("title", "日记上锁了");
        return "diary-lock";
    }

    @PostMapping("/unlock")
    public String unlock(@RequestParam(name = "password", required = false) String password,
                         HttpServletRequest request, Model model) {
        User user = users.findFirstByOrderByIdAsc().orElse(null);
        if (user == null || password == null || !passwordEncoder.matches(password, user.getDiaryHash())) {
            model.addAttribute("title", "日记上锁了");
            model.addAttribute("error", "日记密码不对。");
            return "diary-lock";
        }
        diaryGate.unlock(request.getSession(true));
        return "redirect:/diary";
    }

    @PostMapping("/lock")
    public String lock(HttpServletRequest request) {
        diaryGate.lock(request.getSession(false));
        return "redirect:/";
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("title", "日记");
        model.addAttribute("kind", EntryKind.DIARY);
        model.addAttribute("entries", entries.list(EntryKind.DIARY));
        return "list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Entry entry = entries.find(id, EntryKind.DIARY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到这篇"));
        model.addAttribute("title", entry.getTitle());
        model.addAttribute("kind", EntryKind.DIARY);
        model.addAttribute("entry", entry);
        model.addAttribute("html", markdown.render(entry.getBodyMd()));
        return "entry";
    }
}
