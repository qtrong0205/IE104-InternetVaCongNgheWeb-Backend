package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.CreateVehicleRequest;
import com.nhom6.motofix.dto.request.UpdateVehicleRequest;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.VehicleResponse;
import com.nhom6.motofix.entity.CustomerVehicle;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.repository.CustomerVehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerVehicleService {
    private final CustomerVehicleRepository vehicleRepository;

    /**
     * API 1: Lấy danh sách xe của khách hàng đang đăng nhập.
     * GET /api/users/me/vehicles
     */
    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }
        return vehicleRepository.findByCustomerIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(VehicleResponse::from)
                .toList();
    }

    /**
     * API 2: Thêm xe mới cho khách hàng đang đăng nhập.
     * POST /api/users/me/vehicles
     */
    @Transactional
    public VehicleResponse addVehicle(User currentUser, CreateVehicleRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        CustomerVehicle vehicle = new CustomerVehicle();
        vehicle.setCustomer(currentUser);
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setLicensePlate(request.licensePlate() != null && !request.licensePlate().trim().isEmpty()
                ? request.licensePlate().trim() : null);

        CustomerVehicle saved = vehicleRepository.save(vehicle);
        log.info("User {} added new vehicle {}", currentUser.getId(), saved.getId());
        return VehicleResponse.from(saved);
    }

    /**
     * API 3: Cập nhật thông tin xe của khách hàng.
     * PUT /api/users/me/vehicles/{id}
     * Kiểm tra quyền sở hữu: Người dùng chỉ được sửa xe của chính mình.
     */
    @Transactional
    public VehicleResponse updateVehicle(UUID vehicleId, User currentUser, UpdateVehicleRequest request) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        CustomerVehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        // Kiểm tra quyền sở hữu
        if (!vehicle.getCustomer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not own this vehicle");
        }

        if (request.brand() != null) {
            String trimmedBrand = request.brand().trim();
            if (trimmedBrand.isEmpty()) {
                throw new BadRequestException("Brand cannot be empty");
            }
            vehicle.setBrand(trimmedBrand);
        }

        if (request.model() != null) {
            String trimmedModel = request.model().trim();
            if (trimmedModel.isEmpty()) {
                throw new BadRequestException("Model cannot be empty");
            }
            vehicle.setModel(trimmedModel);
        }

        if (request.licensePlate() != null) {
            String trimmedPlate = request.licensePlate().trim();
            vehicle.setLicensePlate(trimmedPlate.isEmpty() ? null : trimmedPlate);
        }

        CustomerVehicle saved = vehicleRepository.save(vehicle);
        log.info("User {} updated vehicle {}", currentUser.getId(), saved.getId());
        return VehicleResponse.from(saved);
    }

    /**
     * API 4: Xóa xe của khách hàng.
     * DELETE /api/users/me/vehicles/{id}
     * Kiểm tra quyền sở hữu: Không cho phép xóa xe của người khác.
     */
    @Transactional
    public MessageResponse deleteVehicle(UUID vehicleId, User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User is not authenticated");
        }

        CustomerVehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        // Kiểm tra quyền sở hữu
        if (!vehicle.getCustomer().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not own this vehicle");
        }

        vehicleRepository.delete(vehicle);
        log.info("User {} deleted vehicle {}", currentUser.getId(), vehicleId);
        return new MessageResponse("Vehicle deleted successfully");
    }
}

