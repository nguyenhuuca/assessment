package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, String> {
}
