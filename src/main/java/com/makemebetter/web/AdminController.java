package com.makemebetter.web;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.StudyCategory;
import com.makemebetter.service.EntryService;
import com.makemebetter.service.MarkdownService;
import com.makemebetter.service.ViewHelper;
import com.makemebetter.web.form.EntryForm;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final EntryService entries;
    private final MarkdownService markdown;
    private final ViewHelper viewHelper;

    public AdminController(EntryService entries, MarkdownService markdown, ViewHelper viewHelper) {
        this.entries = entries;
        this.markdown = markdown;
        this.viewHelper = viewHelper;
    }

    // ---------- 列表 ----------

    @GetMapping
    public String index(@RequestParam(name = "kind", required = false) String kind, Model model) {
        model.addAttribute("title", "管理");
        model.addAttribute("filter", kind == null ? "" : kind);
        model.addAttribute("entries", entries.list(kind));
        model.addAttribute("counts", counts());
        return "admin/index";
    }

    // ---------- 新建 ----------

    /** 带 kind 进来就是「只能写这一栏」（学习页的写一篇）；不带 kind 才给三选一 */
    @GetMapping("/new")
    public String createForm(@RequestParam(name = "kind", required = false) String kind, Model model) {
        boolean picker = kind == null || kind.isBlank();
        model.addAttribute("title", "写一篇");
        model.addAttribute("mode", "new");
        model.addAttribute("picker", picker);
        model.addAttribute("kinds", EntryKind.values());
        model.addAttribute("categories", StudyCategory.values());
        model.addAttribute("formAction", "/admin/entries");
        model.addAttribute("form", EntryForm.forKind(picker ? EntryKind.STUDY.getCode() : kind));
        return "admin/editor";
    }

    @PostMapping("/entries")
    public String create(@ModelAttribute("form") EntryForm form, Model model) {
        String error = validate(form);
        if (error != null) {
            return editorWithError(model, "new", form, error);
        }
        EntryKind kind = EntryKind.fromCode(form.getKind());
        boolean study = kind == EntryKind.STUDY;
        Entry saved = entries.create(kind, form.getTitle().strip(), safe(form.getBodyMd()),
                study ? "" : safe(form.getMood()),
                tagsOf(form, study),
                study ? categoryOf(form.getCategory()) : "",
                study ? LocalDate.now() : entryDate(form.getEntryDate()));
        return "redirect:" + kind.getPath() + "/" + saved.getId() + "?ok=saved";
    }

    // ---------- 改 ----------

    @GetMapping("/entries/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Entry entry = entries.find(id).orElseThrow(() -> notFound());
        model.addAttribute("title", "改一篇");
        model.addAttribute("mode", "edit");
        model.addAttribute("picker", false);
        model.addAttribute("kinds", EntryKind.values());
        model.addAttribute("categories", StudyCategory.values());
        model.addAttribute("formAction", "/admin/entries/" + entry.getId());
        model.addAttribute("viewUrl", entry.getKind().getPath() + "/" + entry.getId());
        model.addAttribute("form", toForm(entry));
        return "admin/editor";
    }

    @PostMapping("/entries/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") EntryForm form, Model model) {
        Entry entry = entries.find(id).orElseThrow(() -> notFound());
        String error = validate(form);
        if (error != null) {
            return editorWithError(model, "edit", form, error);
        }
        EntryKind kind = EntryKind.fromCode(form.getKind());
        boolean study = kind == EntryKind.STUDY;
        entries.update(entry, kind, form.getTitle().strip(), safe(form.getBodyMd()),
                study ? "" : safe(form.getMood()),
                tagsOf(form, study),
                study ? categoryOf(form.getCategory()) : "",
                // 学习的日期是发布那天，编辑时不动
                study ? entry.getEntryDate() : entryDate(form.getEntryDate()));
        return "redirect:" + kind.getPath() + "/" + entry.getId() + "?ok=saved";
    }

    @PostMapping("/entries/{id}/delete")
    public String delete(@PathVariable Long id) {
        entries.delete(id);
        return "redirect:/admin?ok=deleted";
    }

    // ---------- 预览 ----------

    @PostMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String preview(@RequestParam(name = "bodyMd", required = false) String bodyMd) {
        return markdown.render(bodyMd);
    }

    // ---------- 小工具 ----------

    private String validate(EntryForm form) {
        if (form.getTitle() == null || form.getTitle().isBlank()) {
            return "标题不能是空的。";
        }
        if (form.getTitle().length() > 120) {
            return "标题太长了，120 个字以内。";
        }
        return null;
    }

    private String editorWithError(Model model, String mode, EntryForm form, String error) {
        model.addAttribute("title", "new".equals(mode) ? "写一篇" : "改一篇");
        model.addAttribute("mode", mode);
        model.addAttribute("picker", false);
        model.addAttribute("kinds", EntryKind.values());
        model.addAttribute("categories", StudyCategory.values());
        model.addAttribute("error", error);
        model.addAttribute("formAction", "new".equals(mode) ? "/admin/entries" : "/admin/entries/" + form.getId());
        if (!"new".equals(mode) && form.getId() != null) {
            model.addAttribute("viewUrl", EntryKind.fromCode(form.getKind()).getPath() + "/" + form.getId());
        }
        model.addAttribute("form", form);
        return "admin/editor";
    }

    private EntryForm toForm(Entry entry) {
        EntryForm form = new EntryForm();
        form.setId(entry.getId());
        form.setKind(entry.getKind().getCode());
        form.setTitle(entry.getTitle());
        form.setBodyMd(entry.getBodyMd());
        form.setMood(entry.getMood());
        form.setTags(entry.getTags());
        form.setCategory(entry.getCategory() == null ? "" : entry.getCategory());
        form.setEntryDate(entry.getEntryDate());
        return form;
    }

    /** 学习只收一个标签：逗号后面的一律不要；其它类型还是按逗号拆 */
    private String tagsOf(EntryForm form, boolean study) {
        if (!study) {
            return viewHelper.normalizeTags(form.getTags());
        }
        String raw = safe(form.getTags()).strip();
        String first = raw.split("[,，]")[0].strip();
        return first.length() > 30 ? first.substring(0, 30) : first;
    }

    /** 分类只认枚举里的 code，认不出来就当没分类 */
    private String categoryOf(String code) {
        StudyCategory category = StudyCategory.fromCode(code);
        return category == null ? "" : category.getCode();
    }

    private Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EntryKind kind : EntryKind.values()) {
            counts.put(kind.getCode(), entries.count(kind));
        }
        return counts;
    }

    private LocalDate entryDate(LocalDate value) {
        return value == null ? LocalDate.now() : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到");
    }
}
