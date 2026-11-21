package com.makemebetter.web;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.service.EntryService;
import com.makemebetter.service.MarkdownService;
import com.makemebetter.service.StudyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class EntryController {

    private final EntryService entries;
    private final StudyService study;
    private final MarkdownService markdown;

    public EntryController(EntryService entries, StudyService study, MarkdownService markdown) {
        this.entries = entries;
        this.study = study;
        this.markdown = markdown;
    }

    @GetMapping("/study")
    public String list(@RequestParam(name = "cat", required = false) String cat,
                       @RequestParam(name = "sort", required = false) String sort,
                       @RequestParam(name = "tag", required = false) String tag, Model model) {
        model.addAttribute("title", "学习");
        model.addAttribute("kind", EntryKind.STUDY);
        model.addAttribute("page", study.page(cat, sort, tag));
        return "study";
    }

    @GetMapping("/study/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Entry entry = entries.find(id, EntryKind.STUDY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到这篇"));
        model.addAttribute("title", entry.getTitle());
        model.addAttribute("kind", EntryKind.STUDY);
        model.addAttribute("entry", entry);
        model.addAttribute("html", markdown.render(entry.getBodyMd()));
        return "entry";
    }
}
