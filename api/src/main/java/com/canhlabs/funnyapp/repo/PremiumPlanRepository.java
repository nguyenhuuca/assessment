package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.PremiumPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PremiumPlanRepository extends JpaRepository<PremiumPlan, Long> {

    List<PremiumPlan> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<PremiumPlan> findAllByOrderBySortOrderAscIdAsc();

    Optional<PremiumPlan> findByCode(String code);

    boolean existsByCode(String code);
}
