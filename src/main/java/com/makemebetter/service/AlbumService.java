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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    /** 主页那条横条要用的汇总 */
    public record Summary(int albums, int photos, List<String> covers) {
    }

    public static final String INBOX = "_inbox";
    private static final String TRASH = ".trash";
    private static final String THUMBS = "_thumbs";

    private static final int THUMB_WIDTH = 800;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "heif");
    /** Windows / URL 里会出事的字符 */
    private static final Pattern BAD_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private static final int VIEW_WIDTH = 2000;
    /** 浏览器自己认得的格式（嗅文件头判断，不能只看扩展名：HEIC 经常被存成 .jpg） */
    private final Map<String, Boolean> webFormats = new ConcurrentHashMap<>();

    private final Path root;
    private final ImageTools images;
    private final ImageConverter converter;

    public AlbumService(@Value("${app.upload-dir:./data/uploads}") String dir, ImageTools images,
                        ImageConverter converter) {
        this.root = Paths.get(dir).toAbsolutePath().normalize().resolve("albums");
        this.images = images;
        this.converter = converter;
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

    /** 图集数 + 照片数 + 几张缩略图，给主页那条横条用 */
    public Summary summary() {
        List<Album> list = albums();
        List<String> covers = new ArrayList<>();
        for (Album album : list) {
            int taken = 0;
            for (Photo photo : photos(album.name())) {
                covers.add(photo.thumbUrl());
                taken++;
                if (taken >= 3 || covers.size() >= 5) {
                    break;
                }
            }
            if (covers.size() >= 5) {
                break;
            }
        }
        int photos = 0;
        for (Album album : list) {
            photos += album.count();
        }
        return new Summary(list.size(), photos, covers);
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
                String url = isWebFormat(file)
                        ? "/files/albums/" + encode(album) + "/" + encode(name)
                        : "/album/view/" + encode(album) + "/" + encode(name);
                photos.add(new Photo(name, url,
                        "/album/thumb/" + encode(album) + "/" + encode(name) + "?v=2",
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

    /** 缩略图结果：jpeg=true 表示这是生成好的 JPEG，false 表示直接给原图 */
    public record Thumb(Path path, boolean jpeg) {
    }

    /**
     * 缩略图：没有就现生成一张 800px 宽的。
     * ImageIO 解不了的（HEIC 这类）先用 ffmpeg 转一张 JPEG，再缩。
     * 转不出来时：浏览器认得的格式（webp）直接给原图，认不得的返回 null（调用方 404，
     * 绝不能再把原图当 JPEG 发出去，那会被浏览器缓存成一张坏图）。
     */
    public Thumb thumb(String album, String name) {
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
            return new Thumb(thumb, true);
        }
        try {
            BufferedImage image = ImageIO.read(src.toFile());
            if (image == null) {
                Path view = ensureViewFile(dir, name);
                if (view == null) {
                    return isWebFormat(src) ? new Thumb(src, false) : null;
                }
                image = ImageIO.read(view.toFile());
                if (image == null) {
                    return isWebFormat(src) ? new Thumb(src, false) : null;
                }
            }
            Files.createDirectories(thumbDir);
            Files.write(thumb, images.toJpeg(images.fitWidth(image, THUMB_WIDTH), 0.82f));
            return new Thumb(thumb, true);
        } catch (IOException | RuntimeException e) {
            return isWebFormat(src) ? new Thumb(src, false) : null;
        }
    }

    /** 给浏览器看得懂的大图：web 格式直接给原图，HEIC 这类给转好的 JPEG */
    public Path view(String album, String name) {
        Path dir = folder(album);
        if (dir == null) {
            return null;
        }
        Path src = safeResolve(dir, name);
        if (src == null || isWebFormat(src)) {
            return null;
        }
        return ensureViewFile(dir, name);
    }

    /** 生成（或复用）转好的大图：_thumbs/{名字}_v.jpg，最长边 2000 */
    private Path ensureViewFile(Path dir, String name) {
        Path thumbDir = dir.resolve(THUMBS);
        Path view = thumbDir.resolve(baseOf(name) + "_v.jpg");
        if (Files.isRegularFile(view)) {
            return view;
        }
        Path src = safeResolve(dir, name);
        if (src == null) {
            return null;
        }
        Path raw = thumbDir.resolve(baseOf(name) + "_raw.jpg");
        Path converted = converter.toJpeg(src, raw).orElse(null);
        if (converted == null) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(converted.toFile());
            if (image == null) {
                return null;
            }
            Files.createDirectories(thumbDir);
            Files.write(view, images.toJpeg(images.fitWidth(image, VIEW_WIDTH), 0.86f));
            return view;
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            try {
                Files.deleteIfExists(raw);
            } catch (IOException ignored) {
                // 临时文件删不掉无所谓
            }
        }
    }

    /**
     * 这个文件浏览器能不能直接显示。不能只看扩展名：iPhone 的 HEIC 经常被存成 .jpg，
     * 所以读文件头判断。
     */
    private boolean isWebFormat(Path file) {
        String key = file.getFileName().toString();
        Boolean cached = webFormats.get(key);
        if (cached != null) {
            return cached;
        }
        boolean web = sniffsAsWebImage(file);
        webFormats.put(key, web);
        return web;
    }

    private static boolean sniffsAsWebImage(Path file) {
        byte[] head = new byte[12];
        try (var in = Files.newInputStream(file)) {
            if (in.readNBytes(head, 0, head.length) < head.length) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        int b0 = head[0] & 0xFF;
        int b1 = head[1] & 0xFF;
        if (b0 == 0xFF && b1 == 0xD8) {
            return true;   // JPEG
        }
        if (b0 == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return true;   // PNG
        }
        String ascii = new String(head, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (ascii.startsWith("GIF")) {
            return true;
        }
        return ascii.startsWith("RIFF") && ascii.startsWith("WEBP", 8);
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
            Files.deleteIfExists(dir.resolve(THUMBS).resolve(baseOf(name) + "_v.jpg"));
        } catch (IOException ignored) {
            // 衍生文件删不掉无所谓，下次会重新生成
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
            // ImageIO 解不了：HEIC 可以留着，显示时再用 ffmpeg 转
            if (looksLikeHeif(bytes)) {
                return;
            }
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

    /** ftyp box 里的 brand 是 heic / mif1 / msf1 / avif 之类，说明是 HEIF 家族 */
    private static boolean looksLikeHeif(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        String ascii = new String(bytes, 4, 8, StandardCharsets.ISO_8859_1);
        if (!ascii.startsWith("ftyp")) {
            return false;
        }
        String brand = ascii.substring(4);
        return brand.startsWith("hei") || brand.equals("mif1") || brand.equals("msf1")
                || brand.startsWith("avi");
    }
}
