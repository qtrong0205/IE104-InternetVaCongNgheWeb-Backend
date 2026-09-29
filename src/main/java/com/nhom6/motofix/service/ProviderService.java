package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.mapper.ProviderProfileMapper;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderService {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderProfileMapper providerProfileMapper;

    public ProviderProfileResponse uploadProfile(
        Jwt jwt,
        ProviderProfileRequest request
    ){
        // Lấy user id từ access token
        String rawId = jwtService.extractUserId(jwt);
        UUID userId = UUID.fromString(rawId);

        // Kiểm tra user tồn tại hay chưa
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not exist"));

        // Kiểm trả profile tồn tại chưa
        if(providerProfileRepository.existByUserId(userId)){
            throw new BadRequestException("Profile already existed");
        }

        // Tạo entity từ request và lưu vào repository
        var providerProfile = providerProfileMapper.toEntity(request);
        providerProfile.setUser(user);
        providerProfileRepository.save(providerProfile);

        return providerProfileMapper.toResponse(providerProfile);
    }
}
