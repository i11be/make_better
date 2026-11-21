package com.makemebetter.web;

import com.makemebetter.domain.User;
import com.makemebetter.repository.UserRepository;
import com.makemebetter.service.DiaryGate;
import com.makemebetter.service.FileStorage;
import com.makemebetter.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/admin")
public class AdminSettingsController {

    private static final Pattern USERNAME = Pattern.compile("^[\\w.@\\u4e00-\\u9fa5-]{2,24}$");

    private final SettingService settings;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DiaryGate diaryGate;
    private final FileStorage storage;

    public AdminSettingsController(SettingService settings, UserRepository users, PasswordEncoder passwordEncoder,
                                   DiaryGate diaryGate, FileStorage storage) {
        this.settings = settings;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.diaryGate = diaryGate;
        this.storage = storage;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("title", "设置");
        return "admin/settings";
    }

    // ---------- 站点文案 ----------

    @PostMapping("/settings")
    public String saveSettings(@RequestParam(name = "site_title", required = false) String siteTitle,
                               @RequestParam(name = "site_tagline", required = false) String tagline,
                               @RequestParam(name = "owner_name", required = false) String ownerName,
                               @RequestParam(name = "now_md", required = false) String nowMd) {
        settings.setAll(Map.of(
                SettingService.SITE_TITLE, nz(siteTitle),
                SettingService.SITE_TAGLINE, nz(tagline),
                SettingService.OWNER_NAME, nz(ownerName),
                SettingService.NOW_MD, nz(nowMd)));
        return "redirect:/admin/settings?ok=saved";
    }

    // ---------- 个人资料：ID / 签名 / 头像 ----------

    @PostMapping("/profile")
    public String saveProfile(@RequestParam(name = "username", required = false) String username,
                              @RequestParam(name = "bio", required = false) String bio,
                              @RequestParam(name = "avatar", required = false) MultipartFile avatar,
                              @RequestParam(name = "removeAvatar", required = false) String removeAvatar,
                              Model model) {
        User user = currentUser();
        String name = username == null ? "" : username.strip();
        if (!USERNAME.matcher(name).matches()) {
            return settingsWithError(model, "ID 2–24 个字，只能是中文、字母、数字、下划线、点、@ 或短横线。");
        }

        String avatarPath = user.getAvatarPath();
        if (removeAvatar != null) {
            storage.delete(avatarPath);
            avatarPath = "";
        }
        if (avatar != null && !avatar.isEmpty()) {
            try {
                String old = user.getAvatarPath();
                avatarPath = storage.saveAvatar(avatar).path();
                storage.delete(old);
            } catch (RuntimeException e) {
                return settingsWithError(model, e.getMessage());
            }
        }

        user.updateProfile(name, bio, avatarPath);
        users.save(user);
        return "redirect:/admin/settings?ok=profile";
    }

    // ---------- 密码 ----------

    @PostMapping("/password")
    public String changePassword(@RequestParam(name = "current", required = false) String current,
                                 @RequestParam(name = "next", required = false) String next,
                                 @RequestParam(name = "next2", required = false) String next2,
                                 Model model) {
        User user = currentUser();
        String error = checkNewPassword(user, current, next, next2, 8);
        if (error != null) {
            return settingsWithError(model, error);
        }
        user.changePassword(passwordEncoder.encode(next));
        users.save(user);
        return "redirect:/admin/settings?ok=password";
    }

    @PostMapping("/diary-password")
    public String changeDiaryPassword(@RequestParam(name = "current", required = false) String current,
                                      @RequestParam(name = "next", required = false) String next,
                                      @RequestParam(name = "next2", required = false) String next2,
                                      HttpServletRequest request, Model model) {
        User user = currentUser();
        String error = checkNewPassword(user, current, next, next2, 4);
        if (error != null) {
            return settingsWithError(model, error);
        }
        user.changeDiaryPassword(passwordEncoder.encode(next));
        users.save(user);
        diaryGate.lock(request.getSession(false));
        return "redirect:/admin/settings?ok=diary-password";
    }

    // ---------- 小工具 ----------

    private User currentUser() {
        return users.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有账号"));
    }

    private String checkNewPassword(User user, String current, String next, String next2, int minLength) {
        if (current == null || !passwordEncoder.matches(current, user.getPasswordHash())) {
            return "当前登录密码不对。";
        }
        if (next == null || next.length() < minLength) {
            return "新密码至少 " + minLength + " 位。";
        }
        if (!next.equals(next2)) {
            return "两次输入的新密码不一致。";
        }
        return null;
    }

    private String settingsWithError(Model model, String error) {
        model.addAttribute("title", "设置");
        model.addAttribute("error", error);
        return "admin/settings";
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}
