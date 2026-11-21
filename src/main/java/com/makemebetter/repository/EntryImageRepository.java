package com.makemebetter.repository;

import com.makemebetter.domain.EntryImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntryImageRepository extends JpaRepository<EntryImage, Long> {

    List<EntryImage> findByEntryIdOrderBySortAscIdAsc(Long entryId);

    long countByEntryId(Long entryId);

    void deleteByEntryId(Long entryId);
}
