package com.makemebetter.web;

import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.StudySession;
import com.makemebetter.service.AlbumService;
import com.makemebetter.service.ArchiveService;
import com.makemebetter.service.EntryService;
import com.makemebetter.service.MarkdownService;
import com.makemebetter.service.SettingService;
import com.makemebetter.service.StudyTimerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final EntryService entries;
    private final ArchiveService archives;
    private final AlbumService albums;
    private final SettingService settings;
    private final MarkdownService markdown;
    private final StudyTimerService timer;

    public HomeController(EntryService entries, ArchiveService archives, AlbumService albums,
                          SettingService settings, MarkdownService markdown, StudyTimerService timer) {
        this.entries = entries;
        this.archives = archives;
        this.albums = albums;
        this.settings = settings;
        this.markdown = markdown;
        this.timer = timer;
    }

    /** 首页只有一棵世界树，入口都挂在枝上 */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "");
        counts(model);
        return "index";
    }

    /** 树上那个「主页」：头像、ID、签名这些基础资料，外加学习计时和时长统计 */
    @GetMapping("/profile")
    public String profile(Model model) {
        int settled = timer.settleExpired();
        StudySession running = timer.current().orElse(null);

        model.addAttribute("title", "主页");
        counts(model);
        model.addAttribute("albumSummary", albums.summary());
        model.addAttribute("nowHtml", markdown.render(settings.get(SettingService.NOW_MD)));
        model.addAttribute("timer", running);
        model.addAttribute("remainingSeconds", running == null ? 0 : timer.remainingSeconds(running));
        model.addAttribute("timerStats", timer.stats());
        model.addAttribute("justSettled", settled);
        return "profile";
    }

    private void counts(Model model) {
        model.addAttribute("postsCount", entries.count(EntryKind.FEED));
        model.addAttribute("studyCount", entries.count(EntryKind.STUDY));
        model.addAttribute("diaryCount", entries.count(EntryKind.DIARY));
        model.addAttribute("archiveCount", archives.count());
    }
}
