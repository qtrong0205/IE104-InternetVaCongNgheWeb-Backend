package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {
    public boolean existByUserId(UUID userId);
}
