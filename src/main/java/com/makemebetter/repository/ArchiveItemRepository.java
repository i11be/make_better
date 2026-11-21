package com.makemebetter.repository;

import com.makemebetter.domain.ArchiveItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

    List<ArchiveItem> findByArchiveIdOrderBySortAscIdAsc(Long archiveId);

    long countByArchiveId(Long archiveId);
}
