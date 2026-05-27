package com.miaotong.doc.repository;

import com.miaotong.doc.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmployeeId(String employeeId);

    Optional<User> findByEmail(String email);

    boolean existsByEmployeeId(String employeeId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.departmentId = :deptId AND u.isActive = true")
    java.util.List<User> findByDepartmentId(@Param("deptId") Long deptId);

    @Query("SELECT u FROM User u WHERE u.isActive = true AND (u.username LIKE %:keyword% OR u.realName LIKE %:keyword% OR u.employeeId LIKE %:keyword%)")
    java.util.List<User> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT u FROM User u WHERE u.isActive = true ORDER BY u.departmentId, u.realName")
    java.util.List<User> findAllActive();
}
