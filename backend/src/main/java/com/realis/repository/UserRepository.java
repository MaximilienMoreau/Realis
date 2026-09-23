package com.realis.repository;

import com.realis.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(UUID id);
    java.util.List<User> findByDeletedAtIsNotNull();
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
