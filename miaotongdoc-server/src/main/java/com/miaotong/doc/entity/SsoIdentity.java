package com.miaotong.doc.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mt_sso_identity")
public class SsoIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider_id", nullable = false, length = 50)
    private String providerId;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(name = "external_email", length = 200)
    private String externalEmail;

    @Column(name = "external_name", length = 200)
    private String externalName;

    @Column(name = "raw_claims", columnDefinition = "TEXT")
    private String rawClaims;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @PrePersist
    protected void onCreate() {
        linkedAt = LocalDateTime.now();
    }
}
