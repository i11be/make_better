package com.makemebetter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 相册：以文件夹为准，不落数据库。
 * 结构：uploads/albums/{图集}/图片，uploads/albums/_inbox/ 是待整理，
 * 缩略图在同目录的 _thumbs/，删掉的图进 .trash/。
 */
@Service
public class AlbumService {

    /** 一张照片 */
    public record Photo(String name, String url, String thumbUrl, long size) {
    }

    /** 一个图集 */
    public record Album(String name, String url, int count, String coverUrl) {
    }

    public static final String INBOX = "_inbox";
    private static final String TRASH = ".trash";
    private static final String THUMBS = "_thumbs";

    private static final int THUMB_WIDTH = 800;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    /** Windows / URL 里会出事的字符 */
    private static final Pattern BAD_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final Path root;
    private final ImageTools images;

    public AlbumService(@Value("${app.upload-dir:./data/uploads}") String dir, ImageTools images) {
        this.root = Paths.get(dir).toAbsolutePath().normalize().resolve("albums");
        this.images = images;
    }

    // ---------- 读 ----------

    public List<Album> albums() {
        List<Album> list = new ArrayList<>();
        for (String name : listDirNames(root)) {
            if (isReserved(name)) {
                continue;
            }
            List<Photo> photos = photos(name);
            list.add(new Album(name, encode(name), photos.size(),
                    photos.isEmpty() ? "" : photos.get(0).thumbUrl()));
        }
        list.sort(Comparator.comparing(Album::name, AlbumService::naturalCompare));
        return list;
    }

    public List<Photo> inbox() {
        return photos(INBOX);
    }

    /** 所有图集里的照片总数（不含待整理） */
    public int totalCount() {
        int sum = 0;
        for (Album album : albums()) {
            sum += album.count();
        }
        return sum;
    }

    public boolean albumExists(String name) {
        Path dir = folder(name);
        return dir != null && Files.isDirectory(dir);
    }

    public List<Photo> photos(String album) {
        Path dir = folder(album);
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(AlbumService::isImageName)
                    .sorted(AlbumService::naturalCompare)
                    .forEach(names::add);
        } catch (IOException e) {
            return List.of();
        }
        List<Photo> photos = new ArrayList<>();
        for (String name : names) {
            Path file = dir.resolve(name);
            try {
                photos.add(new Photo(name,
                        "/files/albums/" + encode(album) + "/" + encode(name),
                        "/album/thumb/" + encode(album) + "/" + encode(name),
                        Files.size(file)));
            } catch (IOException ignored) {
                // 读不到大小就跳过这一张
            }
        }
        return photos;
    }

    // ---------- 写 ----------

    /** 上传一张图，落到 album（默认待整理）里；返回落盘后的文件名 */
    public String save(String album, MultipartFile file) {
        String target = album == null || album.isBlank() ? INBOX : album.strip();
        if (!INBOX.equals(target) && !validAlbumName(target)) {
            throw new IllegalArgumentException("图集名不合法");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("没有选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("图片超过 10MB 了");
        }

        String original = file.getOriginalFilename() == null ? "photo.jpg" : file.getOriginalFilename();
        original = original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);

        String ext = extension(original);
        if (!EXT.contains(ext)) {
            throw new IllegalArgumentException("只收 jpg / png / gif / webp");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取图片失败", e);
        }
        checkImage(bytes, ext);

        Path dir = root.resolve(target);
        String base = safeBase(original, ext);
        try {
            Files.createDirectories(dir);
            Path dest = unique(dir, base, ext);
            Files.write(dest, bytes);
            return dest.getFileName().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 把一批照片从 from 移到 to（to 不存在就新建）；返回实际移动的张数 */
    public int move(List<String> names, String from, String to) {
        if (!validAlbumName(to)) {
            throw new IllegalArgumentException("图集名不合法");
        }
        Path src = folder(from);
        if (src == null) {
            return 0;
        }
        Path dst = root.resolve(to);
        int moved = 0;
        try {
            Files.createDirectories(dst);
            for (String name : safeNames(names)) {
                Path file = safeResolve(src, name);
                if (file == null) {
                    continue;
                }
                Files.move(file, unique(dst, baseOf(name), extension(name)));
                deleteThumb(src, name);
                moved++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return moved;
    }

    /** 删掉的先扔进 .trash/{原图集}/，不直接销毁 */
    public int trash(String album, List<String> names) {
        Path src = folder(album);
        if (src == null) {
            return 0;
        }
        Path dst = root.resolve(TRASH).resolve(album);
        int moved = 0;
        try {
            Files.createDirectories(dst);
            for (String name : safeNames(names)) {
                Path file = safeResolve(src, name);
                if (file == null) {
                    continue;
                }
                Files.move(file, unique(dst, baseOf(name), extension(name)));
                deleteThumb(src, name);
                moved++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return moved;
    }

    /** 缩略图：没有就现生成一张 800px 宽的；生成不了（比如 webp）就退回原图 */
    public Path thumb(String album, String name) {
        Path dir = folder(album);
        if (dir == null) {
            return null;
        }
        Path src = safeResolve(dir, name);
        if (src == null) {
            return null;
        }
        Path thumbDir = dir.resolve(THUMBS);
        Path thumb = thumbDir.resolve(baseOf(name) + "_s.jpg");
        if (Files.isRegularFile(thumb)) {
            return thumb;
        }
        try {
            BufferedImage image = ImageIO.read(src.toFile());
            if (image == null) {
                return src;
            }
            Files.createDirectories(thumbDir);
            Files.write(thumb, images.toJpeg(images.fitWidth(image, THUMB_WIDTH), 0.82f));
            return thumb;
        } catch (IOException | RuntimeException e) {
            return src;
        }
    }

    // ---------- 名字与路径 ----------

    /** 图集名 → 目录；不合法或不存在返回 null */
    private Path folder(String album) {
        if (album == null || album.isBlank() || album.startsWith(".") || album.contains("..")) {
            return null;
        }
        if (album.contains("/") || album.contains("\\") || BAD_CHARS.matcher(album).find()) {
            return null;
        }
        return root.resolve(album);
    }

    private Path safeResolve(Path dir, String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.contains("..") || name.startsWith(".")) {
            return null;
        }
        Path file = dir.resolve(name).normalize();
        return file.startsWith(dir) && Files.isRegularFile(file) ? file : null;
    }

    private List<String> listDirNames(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean isReserved(String name) {
        return INBOX.equals(name) || TRASH.equals(name) || THUMBS.equals(name) || name.startsWith(".");
    }

    private static boolean isImageName(String name) {
        return !name.startsWith(".") && EXT.contains(extension(name));
    }

    public static boolean validAlbumName(String name) {
        if (name == null) {
            return false;
        }
        String value = name.strip();
        if (value.isEmpty() || value.length() > 40 || value.startsWith(".") || value.contains("..")) {
            return false;
        }
        return !BAD_CHARS.matcher(value).find();
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /** 去掉危险字符、压掉空白、留出扩展名，拼一个安全的文件名 */
    private static String safeBase(String original, String ext) {
        String base = baseOf(original).replaceAll("[\\p{Cntrl}]", " ").strip();
        base = BAD_CHARS.matcher(base).replaceAll(" ");
        base = base.replaceAll("\\s+", " ").strip();
        base = base.replaceAll("^[. ]+|[. ]+$", "");
        if (base.isEmpty()) {
            base = "photo";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100).strip();
        }
        return base;
    }

    /** 同名就加 -2、-3……，绝不覆盖 */
    private static Path unique(Path dir, String base, String ext) {
        String suffix = ext.isEmpty() ? "" : "." + ext;
        Path candidate = dir.resolve(base + suffix);
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(base + "-" + n + suffix);
            n++;
        }
        return candidate;
    }

    private void deleteThumb(Path dir, String name) {
        try {
            Files.deleteIfExists(dir.resolve(THUMBS).resolve(baseOf(name) + "_s.jpg"));
        } catch (IOException ignored) {
            // 缩略图删不掉无所谓，下次会重新生成
        }
    }

    private List<String> safeNames(List<String> names) {
        List<String> out = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    out.add(name.strip());
                }
            }
        }
        return out;
    }

    /** 真的能解码成图片才收；webp 没有 JDK 解码器，只验魔数 */
    private void checkImage(byte[] bytes, String ext) {
        if ("webp".equals(ext)) {
            boolean webp = bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            if (!webp) {
                throw new IllegalArgumentException("这不是一张有效的 webp");
            }
            return;
        }
        BufferedImage image = images.read(bytes);
        if (image == null) {
            throw new IllegalArgumentException("这张图读不出来，换一张试试");
        }
        long pixels = (long) image.getWidth() * image.getHeight();
        if (pixels > MAX_PIXELS) {
            throw new IllegalArgumentException("图片尺寸太大了");
        }
    }

    public static String encode(String segment) {
        return java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 自然序：2.jpg 排在 10.jpg 前面 */
    public static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                long na = 0;
                long nb = 0;
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i)) && i - si < 18) {
                    na = na * 10 + (a.charAt(i) - '0');
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j)) && j - sj < 18) {
                    nb = nb * 10 + (b.charAt(j) - '0');
                    j++;
                }
                if (na != nb) {
                    return Long.compare(na, nb);
                }
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) {
                    return cmp;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
