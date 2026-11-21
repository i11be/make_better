package com.makemebetter.web;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryImage;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.service.EntryService;
import com.makemebetter.service.FeedService;
import com.makemebetter.service.FileStorage;
import com.makemebetter.service.MarkdownService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/feed")
public class FeedController {

    private final EntryService entries;
    private final FeedService feed;
    private final FileStorage storage;
    private final MarkdownService markdown;

    public FeedController(EntryService entries, FeedService feed, FileStorage storage, MarkdownService markdown) {
        this.entries = entries;
        this.feed = feed;
        this.storage = storage;
        this.markdown = markdown;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("title", "动态");
        model.addAttribute("kind", EntryKind.FEED);
        model.addAttribute("posts", feed.all());
        return "feed";
    }

    @PostMapping
    public String create(@RequestParam(name = "body", required = false) String body,
                         @RequestParam(name = "photos", required = false) MultipartFile[] photos,
                         Model model) {
        List<FileStorage.Stored> saved = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (photos != null) {
            for (MultipartFile photo : photos) {
                if (photo == null || photo.isEmpty()) {
                    continue;
                }
                try {
                    saved.add(storage.savePhoto(photo));
                } catch (RuntimeException e) {
                    errors.add(e.getMessage());
                }
            }
        }

        boolean emptyBody = body == null || body.isBlank();
        if (emptyBody && saved.isEmpty()) {
            model.addAttribute("title", "动态");
            model.addAttribute("kind", EntryKind.FEED);
            model.addAttribute("posts", feed.all());
            model.addAttribute("error", errors.isEmpty() ? "写点什么，或者选一张图。" : String.join("；", errors));
            return "feed";
        }

        entries.createPost(body == null ? "" : body.strip(), saved, LocalDate.now());
        return "redirect:/feed?ok=posted";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Entry entry = entries.find(id, EntryKind.FEED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到这条动态"));
        model.addAttribute("title", "动态");
        model.addAttribute("kind", EntryKind.FEED);
        model.addAttribute("entry", entry);
        model.addAttribute("images", feed.imagesOf(entry.getId()));
        model.addAttribute("html", markdown.render(entry.getBodyMd()));
        return "feed-detail";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        entries.delete(id);
        return "redirect:/feed?ok=deleted";
    }
}
