package com.nhom6.motofix.service;

import com.nhom6.motofix.dto.request.ProviderProfileRequest;
import com.nhom6.motofix.dto.respond.ProviderProfileResponse;
import com.nhom6.motofix.entity.ProviderProfile;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.mapper.ProviderProfileMapper;
import com.nhom6.motofix.repository.ProviderProfileRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Lấy danh sách pending
    @Transactional(readOnly =true )
    public Page<ProviderProfileResponse> getPendingProviders(Pageable pageable) {
        return providerProfileRepository
                .findByApprovalStatus(ApprovalStatus.PENDING, pageable)
                .map(providerProfileMapper::toResponse);
    }

    //Approve provider
    @Transactional
    public ProviderProfileResponse approveProvider(UUID providerId) {

        ProviderProfile provider = providerProfileRepository
                .findById(providerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Provider not found")
                );

        if (provider.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(
                    "Only pending providers can be approved"
            );
        }

        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        provider.setRejectionReason(null);
        provider.setActive(true);

        ProviderProfile savedProvider =
                providerProfileRepository.save(provider);

        return providerProfileMapper.toResponse(savedProvider);
    }

    //Reject provider
    @Transactional
    public ProviderProfileResponse rejectProvider(
            UUID providerId,
            String reason
    ) {

        ProviderProfile provider = providerProfileRepository
                .findById(providerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Provider not found")
                );

        if (provider.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(
                    "Only pending providers can be rejected"
            );
        }

        provider.setApprovalStatus(ApprovalStatus.REJECTED);
        provider.setRejectionReason(reason);
        provider.setActive(false);

        ProviderProfile savedProvider =
                providerProfileRepository.save(provider);

        return providerProfileMapper.toResponse(savedProvider);
    }
}
