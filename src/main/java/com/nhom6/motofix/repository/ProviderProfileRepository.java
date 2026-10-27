package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nhom6.motofix.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {
    boolean existsByUserId(UUID userId);
    Page<ProviderProfile> findByApprovalStatus(
            ApprovalStatus approvalStatus,
            Pageable pageable
    );
    @Query("SELECT p.id FROM ProviderProfile p WHERE p.user.id = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);
}
