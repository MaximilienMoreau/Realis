package com.realis.model;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="proof_labels") @Getter @Setter @NoArgsConstructor
public class ProofLabel {
    @Id private UUID recordId;
    private String title = "";
    private String folder = "";
}
