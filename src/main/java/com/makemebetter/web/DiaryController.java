package com.makemebetter.web;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.User;
import com.makemebetter.repository.UserRepository;
import com.makemebetter.service.DiaryGate;
import com.makemebetter.service.EntryService;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 日记：一天一篇，纯文字，当天才能改。
 * 所有日记页面都在 /diary/** 下，所以那道密码锁能盖住全部入口。
 */
@Controller
@RequestMapping("/diary")
public class DiaryController {

    /** 日记没有标题，存成「2026年11月24日」这种，便于后台列表和页面 title */
    private static final DateTimeFormatter TITLE = DateTimeFormatter.ofPattern("yyyy年M月d日");

    private final EntryService entries;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DiaryGate diaryGate;

    public DiaryController(EntryService entries, UserRepository users, PasswordEncoder passwordEncoder,
                           DiaryGate diaryGate) {
        this.entries = entries;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.diaryGate = diaryGate;
    }

    // ---------- 锁 ----------

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

    // ---------- 看 ----------

    @GetMapping
    public String list(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("title", "日记");
        model.addAttribute("today", today);
        model.addAttribute("entries", entries.list(EntryKind.DIARY));
        model.addAttribute("todayEntry", entries.diaryOf(today).orElse(null));
        model.addAttribute("diaryMinutes", diaryGate.minutes());
        return "diary";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Entry entry = diary(id);
        model.addAttribute("title", TITLE.format(entry.getEntryDate()));
        model.addAttribute("entry", entry);
        model.addAttribute("editable", canEdit(entry));
        return "diary-detail";
    }

    // ---------- 写 ----------

    @GetMapping("/new")
    public String newForm(Model model) {
        LocalDate today = LocalDate.now();
        Entry existing = entries.diaryOf(today).orElse(null);
        if (existing != null) {
            return "redirect:/diary/" + existing.getId() + "?ok=exists";
        }
        model.addAttribute("title", "写今天的日记");
        model.addAttribute("today", today);
        model.addAttribute("formAction", "/diary/new");
        model.addAttribute("body", "");
        return "diary-edit";
    }

    @PostMapping("/new")
    public String create(@RequestParam(name = "body", required = false) String body) {
        LocalDate today = LocalDate.now();
        Entry existing = entries.diaryOf(today).orElse(null);
        if (existing != null) {
            return "redirect:/diary/" + existing.getId() + "?ok=exists";
        }
        Entry saved = entries.create(EntryKind.DIARY, TITLE.format(today), plain(body), "", "", "", today);
        return "redirect:/diary/" + saved.getId() + "?ok=saved";
    }

    // ---------- 改 / 删（只限当天） ----------

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Entry entry = diary(id);
        if (!canEdit(entry)) {
            throw locked();
        }
        model.addAttribute("title", "改今天的日记");
        model.addAttribute("today", entry.getEntryDate());
        model.addAttribute("formAction", "/diary/" + id + "/edit");
        model.addAttribute("body", entry.getBodyMd());
        return "diary-edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @RequestParam(name = "body", required = false) String body) {
        Entry entry = diary(id);
        if (!canEdit(entry)) {
            throw locked();
        }
        entries.update(entry, EntryKind.DIARY, entry.getTitle(), plain(body), "", "", "", entry.getEntryDate());
        return "redirect:/diary/" + id + "?ok=saved";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        Entry entry = diary(id);
        if (!canEdit(entry)) {
            throw locked();
        }
        entries.delete(id);
        return "redirect:/diary?ok=deleted";
    }

    // ---------- 小工具 ----------

    private Entry diary(Long id) {
        return entries.find(id, EntryKind.DIARY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到这篇日记"));
    }

    /** 只有今天写的那篇还能改 */
    private boolean canEdit(Entry entry) {
        return LocalDate.now().equals(entry.getEntryDate());
    }

    private ResponseStatusException locked() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "这天的日记已经不能改了。");
    }

    private String plain(String body) {
        return body == null ? "" : body.strip();
    }
}
