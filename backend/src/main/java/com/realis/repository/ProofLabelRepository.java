package com.realis.repository;
import com.realis.model.ProofLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ProofLabelRepository extends JpaRepository<ProofLabel, UUID> {}
