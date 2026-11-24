package com.makemebetter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 把浏览器不认的图片（HEIC / AVIF）转成 JPEG。
 *
 * JDK 自带的 ImageIO 只认 JPEG / PNG / GIF / BMP，解不了 HEIC；
 * 这里借用系统里的 ffmpeg（外部命令，不是 Java 库）转一张 JPEG 出来，当“显示副本”用。
 * 原图一个字节都不动。
 *
 * 命令行可以在 application.yml 里用 {@code app.ffmpeg} 指定，默认就是 PATH 里的 ffmpeg。
 */
@Service
public class ImageConverter {

    private static final Logger log = LoggerFactory.getLogger(ImageConverter.class);
    private static final long TIMEOUT_SECONDS = 30;

    private final String command;
    private volatile Boolean available;

    public ImageConverter(@Value("${app.ffmpeg:ffmpeg}") String command) {
        this.command = command;
    }

    /** ffmpeg 在不在（只探一次，结果缓存） */
    public boolean available() {
        if (available == null) {
            try {
                Process process = new ProcessBuilder(command, "-version")
                        .redirectErrorStream(true)
                        .start();
                process.getInputStream().readAllBytes();
                available = process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                available = false;
            }
            if (!available) {
                log.warn("没找到 {}，HEIC 图片会显示成占位块（装个 ffmpeg 或者配 app.ffmpeg 即可）", command);
            }
        }
        return available;
    }

    /** 转一张 JPEG 到 target；失败返回 empty，调用方自己退回原图 */
    public Optional<Path> toJpeg(Path source, Path target) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            Files.createDirectories(target.getParent());
            Process process = new ProcessBuilder(
                    command,
                    "-v", "error",
                    "-y",
                    "-i", source.toString(),
                    "-frames:v", "1",
                    "-q:v", "3",
                    target.toString())
                    .redirectErrorStream(true)
                    .start();
            // 一定要把输出读掉，不然管道满了进程会卡死
            process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("转图超时：{}", source.getFileName());
            }
            if (finished && process.exitValue() == 0
                    && Files.isRegularFile(target) && Files.size(target) > 0) {
                return Optional.of(target);
            }
            Files.deleteIfExists(target);
            return Optional.empty();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("转图失败：{}（{}）", source.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}
