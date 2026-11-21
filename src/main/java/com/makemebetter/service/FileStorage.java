package com.makemebetter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 图片落盘：数据库里只存相对路径，文件放在 {@code app.upload-dir}（默认 data/uploads）。
 * 目录结构：avatars/xxx.jpg、2026/11/21/xxx.jpg（动态原图）与其 _s.jpg（缩略图）。
 */
@Service
public class FileStorage {

    public record Stored(String path, int width, int height) {
    }

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int AVATAR_SIZE = 400;
    private static final int THUMB_WIDTH = 800;

    private final Path root;
    private final ImageTools images;

    public FileStorage(@Value("${app.upload-dir:./data/uploads}") String dir, ImageTools images) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.images = images;
    }

    public Path root() {
        return root;
    }

    /** 头像：居中裁方，输出 400×400 jpg */
    public Stored saveAvatar(MultipartFile file) {
        BufferedImage image = decode(file);
        String name = UUID.randomUUID() + ".jpg";
        Path target = root.resolve("avatars").resolve(name);
        write(target, images.toJpeg(images.square(image, AVATAR_SIZE), 0.9f));
        return new Stored("avatars/" + name, AVATAR_SIZE, AVATAR_SIZE);
    }

    /** 动态配图：原图按原格式存，另存一张 800px 宽的 jpg 缩略图 */
    public Stored savePhoto(MultipartFile file) {
        BufferedImage image = decode(file);
        String format = formatOf(file.getOriginalFilename());
        String name = UUID.randomUUID().toString().replace("-", "");
        LocalDate today = LocalDate.now();
        String dir = String.format("%04d/%02d/%02d", today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        Path original = root.resolve(dir).resolve(name + "." + format);
        Path thumb = root.resolve(dir).resolve(name + "_s.jpg");
        try {
            Files.createDirectories(original.getParent());
            byte[] raw = file.getBytes();
            Files.write(original, raw);
            write(thumb, images.toJpeg(images.fitWidth(image, THUMB_WIDTH), 0.82f));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Stored(dir + "/" + name + "." + format, image.getWidth(), image.getHeight());
    }

    /** 相对路径 → 真实路径；越界、不存在的返回 empty */
    public Optional<Path> resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    public void delete(String relativePath) {
        resolve(relativePath).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 删不掉就算了，不影响业务
            }
        });
    }

    /** 缩略图路径（约定：同名 + _s.jpg） */
    public static String thumbOf(String path) {
        if (path == null) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path + "_s.jpg" : path.substring(0, dot) + "_s.jpg";
    }

    private void write(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedImage decode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("没有选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("图片超过 10MB 了");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!type.startsWith("image/")) {
            throw new IllegalArgumentException("只能传图片");
        }
        try {
            BufferedImage image = images.read(file.getBytes());
            if (image == null) {
                throw new IllegalArgumentException("这张图读不出来，换一张试试");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("读取图片失败", e);
        }
    }

    private String formatOf(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") ? "png" : "jpg";
    }
}
