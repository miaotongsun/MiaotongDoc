package com.miaotong.doc.repository;

import com.miaotong.doc.entity.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

    List<AiProvider> findByType(String type);

    List<AiProvider> findByEnabledTrue();

    @Query("SELECT a FROM AiProvider a WHERE a.type = :type AND a.isDefault = true")
    Optional<AiProvider> findDefaultByType(@Param("type") String type);

    Optional<AiProvider> findByTypeAndName(String type, String name);
}