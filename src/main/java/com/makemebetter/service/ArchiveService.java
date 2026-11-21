package com.makemebetter.service;

import com.makemebetter.domain.Archive;
import com.makemebetter.domain.ArchiveItem;
import com.makemebetter.repository.ArchiveItemRepository;
import com.makemebetter.repository.ArchiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ArchiveService {

    private final ArchiveRepository archives;
    private final ArchiveItemRepository items;

    public ArchiveService(ArchiveRepository archives, ArchiveItemRepository items) {
        this.archives = archives;
        this.items = items;
    }

    // ---------- 档案 ----------

    public List<Archive> list() {
        return archives.findAllByOrderByUpdatedAtDesc();
    }

    public long count() {
        return archives.count();
    }

    public Optional<Archive> find(Long id) {
        return archives.findById(id);
    }

    @Transactional
    public Archive create(String title, String summary) {
        return archives.save(new Archive(title, summary));
    }

    @Transactional
    public Archive update(Archive archive, String title, String summary) {
        archive.update(title, summary);
        return archives.save(archive);
    }

    @Transactional
    public void delete(Long id) {
        archives.deleteById(id);
    }

    // ---------- 条目 ----------

    public List<ArchiveItem> itemsOf(Long archiveId) {
        return items.findByArchiveIdOrderBySortAscIdAsc(archiveId);
    }

    public long itemCount(Long archiveId) {
        return items.countByArchiveId(archiveId);
    }

    public Optional<ArchiveItem> findItem(Long itemId) {
        return items.findById(itemId);
    }

    @Transactional
    public ArchiveItem addItem(Long archiveId, String title, String bodyMd) {
        int sort = items.findByArchiveIdOrderBySortAscIdAsc(archiveId).size();
        return items.save(new ArchiveItem(archiveId, title, bodyMd, sort));
    }

    @Transactional
    public ArchiveItem updateItem(ArchiveItem item, String title, String bodyMd) {
        item.update(title, bodyMd);
        return items.save(item);
    }

    @Transactional
    public void deleteItem(Long itemId) {
        items.deleteById(itemId);
    }

    /** 上移（-1）/ 下移（+1）。先规整编号，再交换，避免 sort 重复导致混乱。 */
    @Transactional
    public void moveItem(Long archiveId, Long itemId, int direction) {
        List<ArchiveItem> list = items.findByArchiveIdOrderBySortAscIdAsc(archiveId);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(itemId)) {
                index = i;
                break;
            }
        }
        int target = index + direction;
        if (index < 0 || target < 0 || target >= list.size()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            list.get(i).moveTo(i);
        }
        ArchiveItem moving = list.get(index);
        ArchiveItem other = list.get(target);
        moving.moveTo(target);
        other.moveTo(index);
        items.saveAll(list);
    }
}
