package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.mapper.ProviderProfileMapper;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProviderService {
    private final ProviderProfileRepository providerProfileRepository;
    private final ProviderProfileMapper providerProfileMapper;

    public ProviderProfileResponse uploadProfile(
        User user,
        ProviderProfileRequest request
    ){
        // Lấy user id
        UUID userId = user.getId();

        // Kiểm trả profile tồn tại chưa
        if(providerProfileRepository.existsByUserId(userId)){
            throw new BadRequestException("Profile already existed");
        }

        // Tạo entity từ request và lưu vào repository
        var providerProfile = providerProfileMapper.toEntity(request);
        providerProfile.setUser(user);
        providerProfileRepository.save(providerProfile);

        return providerProfileMapper.toResponse(providerProfile);
    }
}
