package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.NearbyProviderView;
import com.nhom6.motofix.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nhom6.motofix.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query(value = """
        SELECT p.id AS id,
               extensions.ST_Distance(p.location, pt.g) AS "distanceM"
        FROM provider_profile p,
             (SELECT extensions.ST_SetSRID(extensions.ST_MakePoint(:lng, :lat), 4326)::extensions.geography AS g) pt
        WHERE p.is_active = TRUE
          AND extensions.ST_DWithin(p.location, pt.g, :radiusM)
        ORDER BY "distanceM"
        LIMIT :limit
        """, nativeQuery = true)
    List<NearbyProviderView> findNearby(@Param("lat") double lat,
                                        @Param("lng") double lng,
                                        @Param("radiusM") double radiusM,
                                        @Param("limit") int limit);
}
