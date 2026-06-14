package com.miaotong.doc.repository;

import com.miaotong.doc.entity.WatermarkConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WatermarkConfigRepository extends JpaRepository<WatermarkConfig, Long> {
    Optional<WatermarkConfig> findByName(String name);
    Optional<WatermarkConfig> findByIsEnabledTrue();
}
