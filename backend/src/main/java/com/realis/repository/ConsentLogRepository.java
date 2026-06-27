package com.realis.repository;

import com.realis.model.ConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsentLogRepository extends JpaRepository<ConsentLog, UUID> {}
