package com.miaotong.doc.repository;

import com.miaotong.doc.entity.SsoIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SsoIdentityRepository extends JpaRepository<SsoIdentity, Long> {

    Optional<SsoIdentity> findByProviderIdAndExternalId(String providerId, String externalId);

    List<SsoIdentity> findByUserId(Long userId);

    @Query("SELECT si FROM SsoIdentity si WHERE si.externalEmail = :email")
    Optional<SsoIdentity> findByExternalEmail(@Param("email") String email);

    boolean existsByProviderIdAndExternalId(String providerId, String externalId);

    void deleteByUserId(Long userId);
}
