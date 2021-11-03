package com.canhlabs.assessment.repo;

import com.canhlabs.assessment.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    User findAllByUserName(String email);
}
