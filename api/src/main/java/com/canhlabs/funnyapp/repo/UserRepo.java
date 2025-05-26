package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    User findAllByUserName(String email);
    User findAllById(Long id);
}
