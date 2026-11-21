package com.makemebetter.web;

import com.makemebetter.domain.Archive;
import com.makemebetter.domain.ArchiveItem;
import com.makemebetter.service.ArchiveService;
import com.makemebetter.service.MarkdownService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Controller
public class ArchiveController {

    /** 档案列表里的一张卡片 */
    public record Card(Archive archive, long itemCount) {
    }

    private final ArchiveService archives;
    private final MarkdownService markdown;

    public ArchiveController(ArchiveService archives, MarkdownService markdown) {
        this.archives = archives;
        this.markdown = markdown;
    }

    @GetMapping("/archive")
    public String list(Model model) {
        List<Card> cards = new ArrayList<>();
        for (Archive archive : archives.list()) {
            cards.add(new Card(archive, archives.itemCount(archive.getId())));
        }
        model.addAttribute("title", "档案");
        model.addAttribute("cards", cards);
        return "archive/list";
    }

    @GetMapping("/archive/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(name = "item", required = false) Long itemId,
                         Model model) {
        Archive archive = archives.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到这个档案"));
        List<ArchiveItem> items = archives.itemsOf(id);

        ArchiveItem current = null;
        if (itemId != null) {
            current = items.stream().filter(i -> i.getId().equals(itemId)).findFirst().orElse(null);
        }
        if (current == null && !items.isEmpty()) {
            current = items.get(0);
        }

        model.addAttribute("title", archive.getTitle());
        model.addAttribute("archive", archive);
        model.addAttribute("items", items);
        model.addAttribute("current", current);
        model.addAttribute("html", current == null ? "" : markdown.render(current.getBodyMd()));
        return "archive/detail";
    }
}
