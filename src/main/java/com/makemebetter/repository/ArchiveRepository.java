package com.makemebetter.repository;

import com.makemebetter.domain.Archive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {

    List<Archive> findAllByOrderByUpdatedAtDesc();
}
