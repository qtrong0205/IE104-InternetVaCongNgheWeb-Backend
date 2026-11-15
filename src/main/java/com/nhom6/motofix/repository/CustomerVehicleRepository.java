package com.nhom6.motofix.repository;

import com.nhom6.motofix.entity.CustomerVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerVehicleRepository extends JpaRepository<CustomerVehicle, UUID> {
    List<CustomerVehicle> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<CustomerVehicle> findByIdAndCustomerId(UUID id, UUID customerId);
}

