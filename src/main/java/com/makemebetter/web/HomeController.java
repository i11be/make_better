package com.makemebetter.web;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.service.ArchiveService;
import com.makemebetter.service.DiaryGate;
import com.makemebetter.service.EntryService;
import com.makemebetter.service.FeedService;
import com.makemebetter.service.MarkdownService;
import com.makemebetter.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final EntryService entries;
    private final FeedService feed;
    private final ArchiveService archives;
    private final SettingService settings;
    private final MarkdownService markdown;
    private final DiaryGate diaryGate;

    public HomeController(EntryService entries, FeedService feed, ArchiveService archives, SettingService settings,
                          MarkdownService markdown, DiaryGate diaryGate) {
        this.entries = entries;
        this.feed = feed;
        this.archives = archives;
        this.settings = settings;
        this.markdown = markdown;
        this.diaryGate = diaryGate;
    }

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        boolean unlocked = diaryGate.isUnlocked(request.getSession(false));
        List<Entry> diary = unlocked ? entries.latest(EntryKind.DIARY, 3) : List.of();

        model.addAttribute("title", "");
        model.addAttribute("posts", feed.latest(3));
        model.addAttribute("study", entries.latest(EntryKind.STUDY, 3));
        model.addAttribute("diary", diary);
        model.addAttribute("nowHtml", markdown.render(settings.get(SettingService.NOW_MD)));
        model.addAttribute("postsCount", entries.count(EntryKind.FEED));
        model.addAttribute("studyCount", entries.count(EntryKind.STUDY));
        model.addAttribute("diaryCount", entries.count(EntryKind.DIARY));
        model.addAttribute("archiveCount", archives.count());
        return "index";
    }
}
