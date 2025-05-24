package com.canhlabs.assessment.repo;

import com.canhlabs.assessment.domain.ShareLink;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareLinkRepo extends JpaRepository<ShareLink, Long> {
    @NotNull
    List<ShareLink> findByOrderByCreatedAtDesc();
}