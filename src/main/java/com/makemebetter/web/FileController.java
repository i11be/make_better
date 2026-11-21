package com.makemebetter.web;

import com.makemebetter.service.FileStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** 图片输出：不直接暴露磁盘目录，路径越界一律 404。 */
@Controller
public class FileController {

    private final FileStorage storage;

    public FileController(FileStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/files/{*relative}")
    public ResponseEntity<Resource> serve(@PathVariable String relative) {
        // {*relative} 会把开头的 / 也带进来
        String path = relative == null ? "" : relative.replaceFirst("^/+", "");
        Optional<Path> file = storage.resolve(path);
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(file.get()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(file.get()));
    }

    private MediaType mediaTypeOf(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (name.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        return MediaType.IMAGE_JPEG;
    }
}
