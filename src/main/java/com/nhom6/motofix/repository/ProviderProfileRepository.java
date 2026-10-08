package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nhom6.motofix.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {
    public boolean existsByUserId(UUID userId);
    Page<ProviderProfile> findByApprovalStatus(
            ApprovalStatus approvalStatus,
            Pageable pageable
    );
}
