package com.nhom6.motofix.controller;

import com.nhom6.motofix.dto.request.CreateVehicleRequest;
import com.nhom6.motofix.dto.request.UpdateVehicleRequest;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.VehicleResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.service.CustomerVehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/vehicles")
@RequiredArgsConstructor
public class CustomerVehicleController {
    private final CustomerVehicleService vehicleService;

    /**
     * API 1: Lấy danh sách xe của khách hàng đang đăng nhập.
     * GET /api/users/me/vehicles
     */
    @GetMapping
    public ResponseEntity<List<VehicleResponse>> getMyVehicles(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(vehicleService.getMyVehicles(user));
    }

    /**
     * API 2: Thêm xe mới cho khách hàng đang đăng nhập.
     * POST /api/users/me/vehicles
     */
    @PostMapping
    public ResponseEntity<VehicleResponse> addVehicle(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateVehicleRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(vehicleService.addVehicle(user, request));
    }

    /**
     * API 3: Cập nhật thông tin xe.
     * PUT /api/users/me/vehicles/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, user, request));
    }

    /**
     * API 4: Xóa xe.
     * DELETE /api/users/me/vehicles/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(vehicleService.deleteVehicle(id, user));
    }
}

