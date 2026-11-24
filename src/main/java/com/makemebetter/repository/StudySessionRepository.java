package com.makemebetter.repository;

import com.makemebetter.domain.SessionStatus;
import com.makemebetter.domain.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findFirstByStatusOrderByIdDesc(SessionStatus status);

    List<StudySession> findByStatus(SessionStatus status);
}
