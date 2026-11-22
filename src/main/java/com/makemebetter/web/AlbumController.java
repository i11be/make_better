package com.makemebetter.web;

import com.makemebetter.service.AlbumService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** 相册：图集列表、图墙、上传、归类、回收站。 */
@Controller
@RequestMapping("/album")
public class AlbumController {

    private final AlbumService albums;

    public AlbumController(AlbumService albums) {
        this.albums = albums;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("title", "相册");
        model.addAttribute("inbox", albums.inbox());
        model.addAttribute("albums", albums.albums());
        return "album/list";
    }

    @GetMapping("/{name}")
    public String detail(@PathVariable String name, Model model) {
        if (AlbumService.INBOX.equals(name) || !albums.albumExists(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没找到这个图集");
        }
        model.addAttribute("title", name);
        model.addAttribute("album", name);
        model.addAttribute("photos", albums.photos(name));
        model.addAttribute("albums", albums.albums());
        return "album/detail";
    }

    /** 缩略图：第一次访问时现生成，之后直接读 */
    @GetMapping("/thumb/{name}/{file}")
    public ResponseEntity<Resource> thumb(@PathVariable String name, @PathVariable String file) {
        Path path = albums.thumb(name, file);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(new FileSystemResource(path));
    }

    @PostMapping("/upload")
    public String upload(@RequestParam(name = "album", required = false) String album,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes redirect) {
        String target = album == null || album.isBlank() ? AlbumService.INBOX : album.strip();
        try {
            albums.save(target, file);
            redirect.addFlashAttribute("message", AlbumService.INBOX.equals(target)
                    ? "传好了，放在「待整理」里"
                    : "传好了，进了「" + target + "」");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + backTo(target);
    }

    @PostMapping("/move")
    public String move(@RequestParam(name = "names", required = false) List<String> names,
                       @RequestParam(name = "from", required = false) String from,
                       @RequestParam(name = "to", required = false) String to,
                       @RequestParam(name = "newAlbum", required = false) String newAlbum,
                       RedirectAttributes redirect) {
        String target = newAlbum != null && !newAlbum.isBlank() ? newAlbum.strip() : (to == null ? "" : to.strip());
        try {
            int moved = albums.move(names, from, target);
            redirect.addFlashAttribute(moved > 0 ? "message" : "error",
                    moved > 0 ? "归类了 " + moved + " 张到「" + target + "」" : "先勾几张再归类");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + backTo(from);
    }

    @PostMapping("/trash")
    public String trash(@RequestParam(name = "names", required = false) List<String> names,
                        @RequestParam(name = "from", required = false) String from,
                        RedirectAttributes redirect) {
        try {
            int moved = albums.trash(from, names);
            redirect.addFlashAttribute(moved > 0 ? "message" : "error",
                    moved > 0 ? "移到回收站了（" + moved + " 张，在 .trash 里）" : "先勾几张再删");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + backTo(from);
    }

    private String backTo(String album) {
        if (album == null || album.isBlank() || AlbumService.INBOX.equals(album)) {
            return "/album";
        }
        return "/album/" + AlbumService.encode(album);
    }
}
