package com.makemebetter.repository;

import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByKindOrderByEntryDateDescIdDesc(EntryKind kind);

    List<Entry> findByKindOrderByEntryDateDescIdDesc(EntryKind kind, Pageable pageable);

    List<Entry> findAllByOrderByEntryDateDescIdDesc();

    long countByKind(EntryKind kind);

    Optional<Entry> findByIdAndKind(Long id, EntryKind kind);

    /** 日记一天一篇：按日期找 */
    Optional<Entry> findFirstByKindAndEntryDate(EntryKind kind, LocalDate entryDate);
}
