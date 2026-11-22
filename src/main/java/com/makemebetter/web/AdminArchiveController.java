package com.makemebetter.web;

import com.makemebetter.domain.Archive;
import com.makemebetter.domain.ArchiveItem;
import com.makemebetter.service.ArchiveService;
import com.makemebetter.service.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/archives")
public class AdminArchiveController {

    private final ArchiveService archives;
    private final FileStorage storage;

    public AdminArchiveController(ArchiveService archives, FileStorage storage) {
        this.archives = archives;
        this.storage = storage;
    }

    @GetMapping
    public String list(Model model) {
        List<Archive> list = archives.list();
        model.addAttribute("title", "档案管理");
        model.addAttribute("archives", list);
        return "admin/archives";
    }

    @PostMapping
    public String create(@RequestParam(name = "title", required = false) String title, Model model) {
        if (title == null || title.isBlank()) {
            model.addAttribute("title", "档案管理");
            model.addAttribute("archives", archives.list());
            model.addAttribute("error", "档案得有个名字。");
            return "admin/archives";
        }
        Archive archive = archives.create(title.strip());
        return "redirect:/admin/archives/" + archive.getId();
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Model model) {
        Archive archive = archives.find(id).orElseThrow(this::notFound);
        model.addAttribute("title", "档案 · " + archive.getTitle());
        model.addAttribute("archive", archive);
        model.addAttribute("items", archives.itemsOf(id));
        return "admin/archive-edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam(name = "title", required = false) String title) {
        Archive archive = archives.find(id).orElseThrow(this::notFound);
        if (title != null && !title.isBlank()) {
            archives.update(archive, title.strip());
        }
        return "redirect:/admin/archives/" + id + "?ok=saved";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        archives.delete(id);
        return "redirect:/admin/archives?ok=deleted";
    }

    // ---------- 条目 ----------

    /** 新建条目：先给一张白纸，点了保存才真的建出来 */
    @GetMapping("/{id}/items/new")
    public String newItem(@PathVariable Long id, Model model) {
        Archive archive = archives.find(id).orElseThrow(this::notFound);
        model.addAttribute("title", "新条目 · " + archive.getTitle());
        model.addAttribute("archive", archive);
        model.addAttribute("item", null);
        model.addAttribute("formAction", "/admin/archives/" + id + "/items");
        model.addAttribute("uploadUrl", "/admin/archives/" + id + "/images");
        return "admin/archive-item-edit";
    }

    @PostMapping("/{id}/items")
    public String addItem(@PathVariable Long id,
                          @RequestParam(name = "title", required = false) String title,
                          @RequestParam(name = "bodyMd", required = false) String bodyMd) {
        ArchiveItem item = archives.addItem(id, titleOf(title), bodyMd == null ? "" : bodyMd);
        return "redirect:/admin/archives/" + id + "/items/" + item.getId() + "?ok=created";
    }

    @GetMapping("/{id}/items/{itemId}")
    public String editItem(@PathVariable Long id, @PathVariable Long itemId, Model model) {
        Archive archive = archives.find(id).orElseThrow(this::notFound);
        ArchiveItem item = archives.findItem(itemId).orElseThrow(this::notFound);
        model.addAttribute("title", item.getTitle() + " · " + archive.getTitle());
        model.addAttribute("archive", archive);
        model.addAttribute("item", item);
        model.addAttribute("formAction", "/admin/archives/" + id + "/items/" + itemId);
        model.addAttribute("uploadUrl", "/admin/archives/" + id + "/images");
        return "admin/archive-item-edit";
    }

    @PostMapping("/{id}/items/{itemId}")
    public String updateItem(@PathVariable Long id, @PathVariable Long itemId,
                             @RequestParam(name = "title", required = false) String title,
                             @RequestParam(name = "bodyMd", required = false) String bodyMd) {
        ArchiveItem item = archives.findItem(itemId).orElseThrow(this::notFound);
        archives.updateItem(item, titleOf(title), bodyMd == null ? "" : bodyMd);
        // 存完留在编辑器里接着写
        return "redirect:/admin/archives/" + id + "/items/" + itemId + "?ok=saved";
    }

    @PostMapping("/{id}/items/{itemId}/delete")
    public String deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        archives.deleteItem(itemId);
        return "redirect:/admin/archives/" + id + "?ok=deleted";
    }

    @PostMapping("/{id}/items/{itemId}/move")
    public String moveItem(@PathVariable Long id, @PathVariable Long itemId,
                           @RequestParam(name = "direction", defaultValue = "down") String direction) {
        archives.moveItem(id, itemId, "up".equals(direction) ? -1 : 1);
        return "redirect:/admin/archives/" + id;
    }

    /** 条目里插图：存盘后把地址交回前端，由前端插进正文 */
    @PostMapping("/{id}/images")
    @ResponseBody
    public Map<String, Object> uploadImage(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file) {
        archives.find(id).orElseThrow(this::notFound);
        FileStorage.Stored stored = storage.saveArchiveImage(id, file);
        return Map.of("url", "/files/" + stored.path(),
                "width", stored.width(),
                "height", stored.height());
    }

    private String titleOf(String title) {
        return (title == null || title.isBlank()) ? "未命名条目" : title.strip();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到");
    }
}
