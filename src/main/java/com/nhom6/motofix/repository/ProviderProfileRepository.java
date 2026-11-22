package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.NearbyProviderView;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.enums.ApprovalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    Optional<ProviderProfile> findByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProviderProfile p WHERE p.user.id = :userId")
    Optional<ProviderProfile> findByUserIdWithLock(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProviderProfile p WHERE p.id = :id")
    Optional<ProviderProfile> findByIdWithLock(@Param("id") UUID id);

    @Query(value = """
        SELECT p.id AS id,
               extensions.ST_Distance(
                   COALESCE(p.location, extensions.ST_SetSRID(extensions.ST_MakePoint(p.base_lng, p.base_lat), 4326)::extensions.geography),
                   pt.g
               ) AS "distanceM"
        FROM provider_profile p,
             (SELECT extensions.ST_SetSRID(extensions.ST_MakePoint(:lng, :lat), 4326)::extensions.geography AS g) pt
        WHERE p.is_active = TRUE
          AND p.approval_status = 'APPROVED'
          AND extensions.ST_DWithin(
              COALESCE(p.location, extensions.ST_SetSRID(extensions.ST_MakePoint(p.base_lng, p.base_lat), 4326)::extensions.geography),
              pt.g,
              LEAST(:radiusM, COALESCE(p.service_radius_km * 1000.0, :radiusM))
          )
        ORDER BY "distanceM"
        LIMIT :limit
        """, nativeQuery = true)
    List<NearbyProviderView> findNearby(@Param("lat") double lat,
                                        @Param("lng") double lng,
                                        @Param("radiusM") double radiusM,
                                        @Param("limit") int limit);

    @Query(value = """
        SELECT p.id AS id,
               extensions.ST_Distance(
                   COALESCE(p.location, extensions.ST_SetSRID(extensions.ST_MakePoint(p.base_lng, p.base_lat), 4326)::extensions.geography),
                   pt.g
               ) AS "distanceM"
        FROM provider_profile p
        JOIN users u ON u.id = p.user_id,
             (SELECT extensions.ST_SetSRID(extensions.ST_MakePoint(:lng, :lat), 4326)::extensions.geography AS g) pt
        WHERE p.is_active = TRUE
          AND p.approval_status = 'APPROVED'
          AND u.status = 'active'
          AND (u.current_role IS NULL OR u.current_role = 'PROVIDER')
          AND p.user_id != :customerId
          AND extensions.ST_DWithin(
              COALESCE(p.location, extensions.ST_SetSRID(extensions.ST_MakePoint(p.base_lng, p.base_lat), 4326)::extensions.geography),
              pt.g,
              LEAST(:radiusM, COALESCE(p.service_radius_km * 1000.0, :radiusM))
          )
          AND NOT EXISTS (
              SELECT 1 FROM bookings b
              WHERE b.provider_id = p.id
                AND b.status IN ('SELECTED_PROVIDER', 'PROVIDER_SELECTED', 'PROVIDER_EN_ROUTE', 'INSPECTING', 'AWAITING_QUOTE_APPROVAL', 'IN_PROGRESS', 'WAITING_PAYMENT')
          )
          AND NOT EXISTS (
              SELECT 1 FROM bookings b
              WHERE b.customer_id = p.user_id
                AND b.status IN ('SEARCHING', 'SELECTED_PROVIDER', 'PROVIDER_SELECTED', 'PROVIDER_EN_ROUTE', 'INSPECTING', 'AWAITING_QUOTE_APPROVAL', 'IN_PROGRESS', 'WAITING_PAYMENT')
          )
          AND NOT EXISTS (
              SELECT 1 FROM booking_offers bo
              WHERE bo.booking_id = :bookingId
                AND bo.provider_id = p.id
                AND bo.status = 'DECLINED'
          )
          AND NOT EXISTS (
              SELECT 1 FROM booking_offers bo
              WHERE bo.booking_id = :bookingId
                AND bo.provider_id = p.id
                AND bo.status IN ('PENDING', 'ACCEPTED')
          )
        ORDER BY "distanceM"
        LIMIT :limit
        """, nativeQuery = true)
    List<NearbyProviderView> findNearbyEligibleProviders(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusM") double radiusM,
            @Param("bookingId") UUID bookingId,
            @Param("customerId") UUID customerId,
            @Param("limit") int limit);
}
