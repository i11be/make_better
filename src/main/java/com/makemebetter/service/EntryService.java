package com.makemebetter.service;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryImage;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.StudyCategory;
import com.makemebetter.repository.EntryImageRepository;
import com.makemebetter.repository.EntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EntryService {

    private final EntryRepository repository;
    private final EntryImageRepository imageRepository;
    private final FileStorage storage;

    public EntryService(EntryRepository repository, EntryImageRepository imageRepository, FileStorage storage) {
        this.repository = repository;
        this.imageRepository = imageRepository;
        this.storage = storage;
    }

    public List<Entry> list(EntryKind kind) {
        return repository.findByKindOrderByEntryDateDescIdDesc(kind);
    }

    public List<Entry> latest(EntryKind kind, int limit) {
        return repository.findByKindOrderByEntryDateDescIdDesc(kind, PageRequest.of(0, limit));
    }

    /** kindCode 为 null 或空时返回全部 */
    public List<Entry> list(String kindCode) {
        if (kindCode == null || kindCode.isBlank()) {
            return repository.findAllByOrderByEntryDateDescIdDesc();
        }
        return list(EntryKind.fromCode(kindCode));
    }

    public long count(EntryKind kind) {
        return repository.countByKind(kind);
    }

    public Optional<Entry> find(Long id) {
        return repository.findById(id);
    }

    public Optional<Entry> find(Long id, EntryKind kind) {
        return repository.findByIdAndKind(id, kind);
    }

    /** 某天的日记（一天一篇） */
    public Optional<Entry> diaryOf(LocalDate date) {
        return repository.findFirstByKindAndEntryDate(EntryKind.DIARY, date);
    }

    public List<EntryImage> imagesOf(Long entryId) {
        return imageRepository.findByEntryIdOrderBySortAscIdAsc(entryId);
    }

    @Transactional
    public Entry create(EntryKind kind, String title, String bodyMd, String mood, String tags, String category,
                        LocalDate entryDate) {
        return repository.save(new Entry(kind, title, bodyMd, mood, tags, category, entryDate));
    }

    /** 动态：文字可以为空，但至少要有一张图 */
    @Transactional
    public Entry createPost(String body, List<FileStorage.Stored> photos, LocalDate entryDate) {
        String title = summarize(body);
        Entry entry = repository.save(new Entry(EntryKind.FEED, title, body, "", "", "", entryDate));
        if (photos != null) {
            int sort = 0;
            for (FileStorage.Stored photo : photos) {
                imageRepository.save(new EntryImage(entry.getId(), photo.path(), photo.width(), photo.height(), sort++));
            }
        }
        return entry;
    }

    @Transactional
    public Entry update(Entry entry, EntryKind kind, String title, String bodyMd, String mood, String tags,
                        String category, LocalDate entryDate) {
        entry.update(kind, title, bodyMd, mood, tags, category, entryDate);
        return repository.save(entry);
    }

    @Transactional
    public void delete(Long id) {
        List<EntryImage> images = imageRepository.findByEntryIdOrderBySortAscIdAsc(id);
        repository.deleteById(id);
        // 行由数据库级联删除，文件得自己清
        images.forEach(image -> {
            storage.delete(image.getPath());
            storage.delete(FileStorage.thumbOf(image.getPath()));
        });
    }

    /** 动态列表页的标题：取正文第一行，太长就截断 */
    private String summarize(String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            return "一条动态";
        }
        String firstLine = text.split("\\R", 2)[0].strip();
        return firstLine.length() > 40 ? firstLine.substring(0, 40) : firstLine;
    }

    /** 第一次建完账号，放两条示例，别让人对着空白发呆。 */
    @Transactional
    public void seedWelcomeIfEmpty() {
        if (repository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        create(EntryKind.STUDY, "为什么要有这个地方", """
                把「学过什么」写下来，和「感觉自己学过」是两件事。

                所以：

                - **学习**：一段笔记、一个卡住的点、一次复盘，都算
                - **动态**：今天吃了什么、见了谁、心情怎么样，配两张图也行
                - **日记**：只写给自己，进这一区要再输一次密码
                - **档案**：一本书、一个游戏，可以慢慢往里添

                > 不用写得好看。先写下来。

                把这条改成你自己的第一句话吧，或者直接删掉它。
                """, "开始", "开始", StudyCategory.BAGU.getCode(), today);

        create(EntryKind.FEED, "今天，站点刚建好", "一件事从零开始的时候，总是最轻的。\n\n今天做完了：把这块地方搭起来。",
                "平静", "第一天", "", today);
    }
}
