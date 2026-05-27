package com.miaotong.doc.repository;

import com.miaotong.doc.entity.SigningRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface SigningRecordRepository extends JpaRepository<SigningRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sr FROM SigningRecord sr WHERE sr.taskId = :taskId AND sr.signerUserId = :userId")
    Optional<SigningRecord> findByTaskIdAndSignerUserIdForUpdate(@Param("taskId") Long taskId, @Param("userId") Long userId);

    List<SigningRecord> findByTaskIdOrderBySignOrderAsc(Long taskId);

    Optional<SigningRecord> findByTaskIdAndSignerUserId(Long taskId, Long signerUserId);

    @Query("SELECT COUNT(sr) FROM SigningRecord sr WHERE sr.taskId = :taskId AND sr.status = 'confirmed'")
    long countConfirmedByTaskId(@Param("taskId") Long taskId);
}
